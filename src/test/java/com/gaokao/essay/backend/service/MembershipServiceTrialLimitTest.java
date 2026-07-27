package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.UserSubscription;
import com.gaokao.essay.backend.model.UserUsageQuota;
import com.gaokao.essay.backend.repository.UserSubscriptionRepository;
import com.gaokao.essay.backend.repository.UserUsageQuotaRepository;
import com.gaokao.essay.backend.security.InMemoryAbuseProtectionStore;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipServiceTrialLimitTest {

  @Test
  void shouldRollbackTotalQuotaWhenDailyQuotaIsExhausted() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialTotalLimit(5);
    properties.getMembership().setTrialDailyLimit(1);

    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.tryConsume("user_1", "ESSAY_DAY_2026-07-10", 1);
    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    ApiException error = assertThrows(ApiException.class, () -> service.reserveEssayAccess(user()));

    assertEquals("TRIAL_DAILY_LIMIT_REACHED", error.getCode());
    assertEquals(0, quotaRepository.findByUserIdAndQuotaType("user_1", "ESSAY_TOTAL").orElseThrow().usedCount());
  }

  @Test
  void shouldExposeDailyQuotaInEntitlementPayload() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialTotalLimit(5);
    properties.getMembership().setTrialDailyLimit(2);

    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.tryConsume("user_1", "ESSAY_TOTAL", 5);
    quotaRepository.tryConsume("user_1", "ESSAY_DAY_2026-07-10", 2);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    Map<String, Object> entitlement = service.getEntitlement(user());

    assertEquals("total+daily", entitlement.get("trialPolicy"));
    assertEquals(5, entitlement.get("trialTotalLimit"));
    assertEquals(1, entitlement.get("trialTotalUsed"));
    assertEquals(2, entitlement.get("trialDailyLimit"));
    assertEquals(1, entitlement.get("trialDailyUsed"));
  }

  private MembershipService serviceAt(
      GaokaoProperties properties,
      InMemoryQuotaRepository quotaRepository,
      Instant instant
  ) {
    return new MembershipService(
        properties,
        quotaRepository,
        new InMemorySubscriptionRepository(),
        new InMemoryAbuseProtectionStore(),
        Clock.fixed(instant, ZoneOffset.UTC)
    );
  }

  private AuthenticatedUser user() {
    Instant now = Instant.parse("2026-07-10T10:00:00Z");
    return new AuthenticatedUser("user_1", "open_1", now, now.plusSeconds(3600));
  }

  private static final class InMemoryQuotaRepository implements UserUsageQuotaRepository {
    private final Map<String, UserUsageQuota> store = new LinkedHashMap<>();

    @Override
    public Optional<UserUsageQuota> findByUserIdAndQuotaType(String userId, String quotaType) {
      return Optional.ofNullable(store.get(key(userId, quotaType)));
    }

    @Override
    public boolean tryConsume(String userId, String quotaType, int limitCount) {
      String key = key(userId, quotaType);
      UserUsageQuota current = store.get(key);
      if (current == null) {
        store.put(key, new UserUsageQuota(userId, quotaType, 1, limitCount, Instant.now()));
        return true;
      }
      if (current.usedCount() >= Math.max(limitCount, 0)) {
        return false;
      }
      store.put(key, new UserUsageQuota(userId, quotaType, current.usedCount() + 1, limitCount, Instant.now()));
      return true;
    }

    @Override
    public void release(String userId, String quotaType) {
      String key = key(userId, quotaType);
      UserUsageQuota current = store.get(key);
      if (current == null) {
        return;
      }
      store.put(key, new UserUsageQuota(
          userId,
          quotaType,
          Math.max(current.usedCount() - 1, 0),
          current.limitCount(),
          Instant.now()
      ));
    }

    private String key(String userId, String quotaType) {
      return userId + "::" + quotaType;
    }
  }

  private static final class InMemorySubscriptionRepository implements UserSubscriptionRepository {
    private final Map<String, UserSubscription> store = new HashMap<>();

    @Override
    public Optional<UserSubscription> findByUserId(String userId) {
      return Optional.ofNullable(store.get(userId));
    }

    @Override
    public UserSubscription save(UserSubscription subscription) {
      store.put(subscription.userId(), subscription);
      return subscription;
    }
  }
}
