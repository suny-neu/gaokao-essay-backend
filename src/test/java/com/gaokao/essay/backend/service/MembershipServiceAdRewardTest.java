package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.UserSubscription;
import com.gaokao.essay.backend.model.UserUsageQuota;
import com.gaokao.essay.backend.repository.UserSubscriptionRepository;
import com.gaokao.essay.backend.repository.UserUsageQuotaRepository;
import com.gaokao.essay.backend.security.InMemoryAbuseProtectionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipServiceAdRewardTest {

  @Test
  void shouldFallThroughToAdRewardWhenTrialExhausted() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.tryConsume("user_1", "ESSAY_TOTAL", 5);
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 1, 50);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    MembershipService.UsageReservation reservation = service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1");

    assertTrue(reservation.countedTrial());
    assertTrue(reservation.quotaTypes().contains("AD_REWARD_CREDITS"));
  }

  @Test
  void shouldRejectWhenBothTrialAndAdRewardExhausted() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.tryConsume("user_1", "ESSAY_TOTAL", 5);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    ApiException error = assertThrows(ApiException.class, () -> service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1"));
    assertEquals("TRIAL_LIMIT_REACHED", error.getCode());
  }

  @Test
  void shouldNotFallThroughToAdRewardWhenDisabled() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setEnabled(false);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.tryConsume("user_1", "ESSAY_TOTAL", 5);
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 5, 50);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    ApiException error = assertThrows(ApiException.class, () -> service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1"));
    assertEquals("TRIAL_LIMIT_REACHED", error.getCode());
  }

  @Test
  void shouldPreferTrialOverAdReward() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 3, 50);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1");

    UserUsageQuota totalQuota = quotaRepository.findByUserIdAndQuotaType("user_1", "ESSAY_TOTAL").orElseThrow();
    assertEquals(1, totalQuota.usedCount());
    UserUsageQuota adRewardQuota = quotaRepository.findByUserIdAndQuotaType("user_1", "AD_REWARD_CREDITS").orElseThrow();
    assertEquals(3, adRewardQuota.usedCount());
  }

  @Test
  void shouldGrantCreditsOnAdReward() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    Map<String, Object> result = service.grantAdReward(user("user_1"), "device_1", "127.0.0.1");

    assertEquals(1, result.get("granted"));
    assertEquals(1, result.get("adRewardCredits"));
    assertEquals(50, result.get("adRewardMaxCredits"));
  }

  @Test
  void shouldEnforceDailyMaxOnAdRewardGrant() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setDailyMax(2);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    service.grantAdReward(user("user_1"), "device_1", "127.0.0.1");
    service.grantAdReward(user("user_1"), "device_1", "127.0.0.1");

    ApiException error = assertThrows(ApiException.class, () -> service.grantAdReward(user("user_1"), "device_1", "127.0.0.1"));
    assertEquals("AD_REWARD_DAILY_LIMIT", error.getCode());
  }

  @Test
  void shouldNotGrantWhenAdRewardDisabled() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setEnabled(false);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    ApiException error = assertThrows(ApiException.class, () -> service.grantAdReward(user("user_1"), "device_1", "127.0.0.1"));
    assertEquals("AD_REWARD_DISABLED", error.getCode());
  }

  @Test
  void shouldExposeAdRewardInfoInEntitlement() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 3, 50);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    Map<String, Object> entitlement = service.getEntitlement(user("user_1"));

    assertEquals(true, entitlement.get("adRewardEnabled"));
    assertEquals(3, entitlement.get("adRewardCredits"));
    assertEquals(50, entitlement.get("adRewardMaxCredits"));
    assertEquals(1, entitlement.get("adRewardGrantPerView"));
  }

  @Test
  void shouldRespectMaxCreditsWhenGranting() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setMaxCredits(5);
    properties.getMembership().getAdReward().setGrantPerView(2);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    service.grantAdReward(user("user_1"), "device_1", "127.0.0.1");
    service.grantAdReward(user("user_1"), "device_1", "127.0.0.1");
    service.grantAdReward(user("user_1"), "device_1", "127.0.0.1");

    Map<String, Object> entitlement = service.getEntitlement(user("user_1"));
    assertEquals(5, entitlement.get("adRewardCredits"));
  }

  private GaokaoProperties newProperties() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialTotalLimit(5);
    properties.getMembership().setTrialDailyLimit(0);
    properties.getMembership().setDeviceDailyLimit(0);
    properties.getMembership().setIpDailyLimit(0);
    properties.getMembership().getAdReward().setEnabled(true);
    properties.getMembership().getAdReward().setGrantPerView(1);
    properties.getMembership().getAdReward().setDailyMax(10);
    properties.getMembership().getAdReward().setMaxCredits(50);
    return properties;
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

  private AuthenticatedUser user(String userId) {
    Instant now = Instant.parse("2026-07-10T10:00:00Z");
    return new AuthenticatedUser(userId, "open_" + userId, now, now.plusSeconds(3600));
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
          userId, quotaType,
          Math.max(current.usedCount() - 1, 0),
          current.limitCount(), Instant.now()
      ));
    }

    @Override
    public void grantCredits(String userId, String quotaType, int amount, int maxCredits) {
      String key = key(userId, quotaType);
      UserUsageQuota current = store.get(key);
      int existing = current == null ? 0 : current.usedCount();
      int granted = Math.min(existing + amount, maxCredits);
      store.put(key, new UserUsageQuota(userId, quotaType, granted, maxCredits, Instant.now()));
    }

    @Override
    public boolean consumeCredit(String userId, String quotaType) {
      String key = key(userId, quotaType);
      UserUsageQuota current = store.get(key);
      if (current == null || current.usedCount() <= 0) {
        return false;
      }
      store.put(key, new UserUsageQuota(
          userId, quotaType,
          current.usedCount() - 1,
          current.limitCount(), Instant.now()
      ));
      return true;
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
