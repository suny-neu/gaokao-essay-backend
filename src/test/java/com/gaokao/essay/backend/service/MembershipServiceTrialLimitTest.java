package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import org.junit.jupiter.api.Test;

class MembershipServiceTrialLimitTest {

  @Test
  void shouldExposeConfiguredMonthlyAnnualAndFounderLifetimePlans() {
    GaokaoProperties properties = new GaokaoProperties();
    MembershipService service = serviceAt(properties, new InMemoryQuotaRepository(), Instant.parse("2026-07-10T10:00:00Z"));

    List<Map<String, Object>> plans = service.getPlans();

    assertEquals(List.of("monthly", "annual", "founder_lifetime"), plans.stream().map(plan -> plan.get("planCode")).toList());
    assertTrue((Integer) plans.get(0).get("durationDays") > 0);
    assertTrue((Integer) plans.get(1).get("durationDays") > 0);
    assertEquals(true, plans.get(2).get("lifetime"));
    assertEquals(0, plans.get(2).get("durationDays"));
    assertEquals("创始终身会员", plans.get(2).get("planName"));
    assertEquals("终身不限量", plans.get(2).get("durationText"));
  }

  @Test
  void shouldActivateFounderLifetimeWithoutExpiryOrTrialQuota() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialDailyLimit(1);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    InMemorySubscriptionRepository subscriptionRepository = new InMemorySubscriptionRepository();
    Instant activatedAt = Instant.parse("2026-07-10T10:00:00Z");
    MembershipService service = new MembershipService(
        properties,
        quotaRepository,
        subscriptionRepository,
        new InMemoryAbuseProtectionStore(),
        Clock.fixed(activatedAt, ZoneOffset.UTC)
    );

    UserSubscription subscription = service.activatePaidSubscription(
        "user_1", "founder_lifetime", "wechatpay", "transaction_1", activatedAt
    );
    MembershipService.UsageReservation reservation = service.reserveEssayAccess(user());

    assertTrue(subscription.isActiveAt(activatedAt.plus(36500, java.time.temporal.ChronoUnit.DAYS)));
    assertNull(subscription.expiresAt());
    assertFalse(reservation.countedTrial());
    assertTrue(quotaRepository.findByUserIdAndQuotaType("user_1", "ESSAY_DAY_2026-07-10").isEmpty());
  }

  @Test
  void shouldKeepFounderLifetimeWhenALateMonthlyCallbackArrives() {
    GaokaoProperties properties = new GaokaoProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    InMemorySubscriptionRepository subscriptionRepository = new InMemorySubscriptionRepository();
    Instant activatedAt = Instant.parse("2026-07-10T10:00:00Z");
    MembershipService service = new MembershipService(
        properties, quotaRepository, subscriptionRepository, new InMemoryAbuseProtectionStore(), Clock.fixed(activatedAt, ZoneOffset.UTC)
    );

    service.activatePaidSubscription("user_1", "founder_lifetime", "wechatpay", "lifetime_tx", activatedAt);
    UserSubscription afterMonthlyCallback = service.activatePaidSubscription(
        "user_1", "monthly", "wechatpay", "monthly_tx", activatedAt.plusSeconds(10)
    );

    assertEquals("founder_lifetime", afterMonthlyCallback.planCode());
    assertNull(afterMonthlyCallback.expiresAt());
    assertEquals("founder_lifetime", subscriptionRepository.findByUserId("user_1").orElseThrow().planCode());
  }

  @Test
  void shouldPromoteMonthlyToFounderLifetimeAndKeepItForRepeatedLateCallbacks() {
    GaokaoProperties properties = new GaokaoProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    InMemorySubscriptionRepository subscriptionRepository = new InMemorySubscriptionRepository();
    Instant activatedAt = Instant.parse("2026-07-10T10:00:00Z");
    MembershipService service = new MembershipService(
        properties, quotaRepository, subscriptionRepository, new InMemoryAbuseProtectionStore(), Clock.fixed(activatedAt, ZoneOffset.UTC)
    );

    service.activatePaidSubscription("user_1", "monthly", "wechatpay", "monthly_tx", activatedAt);
    service.activatePaidSubscription("user_1", "founder_lifetime", "wechatpay", "lifetime_tx", activatedAt.plusSeconds(10));
    UserSubscription afterRepeatedMonthlyCallback = service.activatePaidSubscription(
        "user_1", "monthly", "wechatpay", "monthly_tx_late", activatedAt.plusSeconds(20)
    );

    assertEquals("founder_lifetime", afterRepeatedMonthlyCallback.planCode());
    assertNull(afterRepeatedMonthlyCallback.expiresAt());
    assertTrue(afterRepeatedMonthlyCallback.isActiveAt(activatedAt.plus(36500, java.time.temporal.ChronoUnit.DAYS)));
  }

  @Test
  void shouldAllowFiveDailyFreeReservationsThenRejectTheSixth() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialTotalLimit(0);
    properties.getMembership().setTrialDailyLimit(5);
    properties.getMembership().setDeviceDailyLimit(0);
    properties.getMembership().setIpDailyLimit(0);

    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    for (int attempt = 0; attempt < 5; attempt += 1) {
      assertDoesNotThrow(() -> service.reserveEssayAccess(user()));
    }
    ApiException error = assertThrows(ApiException.class, () -> service.reserveEssayAccess(user()));

    assertEquals("TRIAL_DAILY_LIMIT_REACHED", error.getCode());
    assertEquals(5, quotaRepository.findByUserIdAndQuotaType("user_1", "ESSAY_DAY_2026-07-10").orElseThrow().usedCount());
  }

  @Test
  void shouldResetDailyFreeReservationsOnTheNextShanghaiDay() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialTotalLimit(0);
    properties.getMembership().setTrialDailyLimit(5);
    properties.getMembership().setDeviceDailyLimit(0);
    properties.getMembership().setIpDailyLimit(0);

    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    MembershipService firstDay = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T15:59:00Z"));
    for (int attempt = 0; attempt < 5; attempt += 1) {
      firstDay.reserveEssayAccess(user());
    }

    MembershipService nextShanghaiDay = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T16:00:00Z"));
    for (int attempt = 0; attempt < 5; attempt += 1) {
      assertDoesNotThrow(() -> nextShanghaiDay.reserveEssayAccess(user()));
    }
  }

  @Test
  void shouldExposeNamedDailyFreeEntitlementFields() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialTotalLimit(0);
    properties.getMembership().setTrialDailyLimit(5);

    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.tryConsume("user_1", "ESSAY_DAY_2026-07-10", 5);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    Map<String, Object> entitlement = service.getEntitlement(user());

    assertEquals(5, entitlement.get("dailyFreeLimit"));
    assertEquals(1, entitlement.get("dailyFreeUsed"));
    assertEquals(4, entitlement.get("dailyFreeRemaining"));
    assertEquals("2026-07-10T16:00:00Z", entitlement.get("dailyResetAt"));
  }

  @Test
  void shouldReleaseEachReservationOnlyOnceAfterALaterReservationSucceeds() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialDailyLimit(1);
    properties.getMembership().setDeviceDailyLimit(0);
    properties.getMembership().setIpDailyLimit(0);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    MembershipService.UsageReservation failedReservation = service.reserveEssayAccess(user());
    service.releaseReservation(failedReservation);
    service.reserveEssayAccess(user());
    service.releaseReservation(failedReservation);

    assertEquals(1, quotaRepository.findByUserIdAndQuotaType("user_1", "ESSAY_DAY_2026-07-10").orElseThrow().usedCount());
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
      releaseCredits(userId, quotaType, 1);
    }

    @Override
    public void releaseCredits(String userId, String quotaType, int amount) {
      String key = key(userId, quotaType);
      UserUsageQuota current = store.get(key);
      if (current == null) {
        return;
      }
      store.put(key, new UserUsageQuota(
          userId,
          quotaType,
          Math.max(current.usedCount() - Math.max(amount, 0), 0),
          current.limitCount(),
          Instant.now()
      ));
    }

    @Override
    public int grantCredits(String userId, String quotaType, int amount, int maxCredits) {
      String key = key(userId, quotaType);
      UserUsageQuota current = store.get(key);
      int existing = current == null ? 0 : current.usedCount();
      int granted = Math.min(Math.max(amount, 0), Math.max(maxCredits, 1));
      if (existing + granted > maxCredits) {
        return 0;
      }
      store.put(key, new UserUsageQuota(userId, quotaType, existing + granted, maxCredits, Instant.now()));
      return granted;
    }

    @Override
    public boolean consumeCredit(String userId, String quotaType) {
      String key = key(userId, quotaType);
      UserUsageQuota current = store.get(key);
      if (current == null || current.usedCount() <= 0) {
        return false;
      }
      store.put(key, new UserUsageQuota(userId, quotaType, current.usedCount() - 1, current.limitCount(), Instant.now()));
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
