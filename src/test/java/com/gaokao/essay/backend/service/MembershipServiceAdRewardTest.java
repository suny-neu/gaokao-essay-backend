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
import com.gaokao.essay.backend.security.AbuseProtectionStore;
import com.gaokao.essay.backend.security.InMemoryAbuseProtectionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipServiceAdRewardTest {

  @Test
  void shouldUseStoredAdCreditAfterDailyFreeQuotaIsExhausted() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    exhaustDailyFreeQuota(quotaRepository, "user_1", 5);
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 1, 50);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    MembershipService.UsageReservation reservation = service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1");

    assertTrue(reservation.countedTrial());
    assertTrue(reservation.quotaTypes().contains("AD_REWARD_CREDITS"));
  }

  @Test
  void shouldRejectWhenDailyFreeAndAdRewardCreditsAreExhausted() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    exhaustDailyFreeQuota(quotaRepository, "user_1", 5);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    ApiException error = assertThrows(ApiException.class, () -> service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1"));
    assertEquals("TRIAL_DAILY_LIMIT_REACHED", error.getCode());
  }

  @Test
  void shouldConsumeAlreadyIssuedAdRewardCreditsWhenNewRewardsAreDisabled() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setEnabled(false);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    exhaustDailyFreeQuota(quotaRepository, "user_1", 5);
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 5, 50);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    MembershipService.UsageReservation reservation = service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1");

    assertTrue(reservation.countedTrial());
    assertTrue(reservation.quotaTypes().contains("AD_REWARD_CREDITS"));
  }

  @Test
  void shouldPreferTrialOverAdReward() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 3, 50);

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    service.reserveEssayAccess(user("user_1"), "device_1", "127.0.0.1");

    UserUsageQuota dailyQuota = quotaRepository.findByUserIdAndQuotaType("user_1", "ESSAY_DAY_2026-07-10").orElseThrow();
    assertEquals(1, dailyQuota.usedCount());
    UserUsageQuota adRewardQuota = quotaRepository.findByUserIdAndQuotaType("user_1", "AD_REWARD_CREDITS").orElseThrow();
    assertEquals(3, adRewardQuota.usedCount());
  }

  @Test
  void shouldGrantCreditsOnAdReward() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    Map<String, Object> result = settleAdReward(service, "user_1", "device_1", "127.0.0.1");

    assertEquals(1, result.get("granted"));
    assertEquals(1, result.get("adRewardCredits"));
    assertEquals(50, result.get("adRewardMaxCredits"));
  }

  @Test
  void shouldRejectDirectAdRewardGrantWithoutAServerIssuedClaim() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    ApiException error = assertThrows(ApiException.class, () ->
        service.grantAdReward(user("user_1"), "device_1", "127.0.0.1")
    );

    assertEquals("AD_REWARD_CLAIM_REQUIRED", error.getCode());
  }

  @Test
  void shouldConsumeAnAdRewardClaimOnceOnlyForItsUserDeviceAndNotBeforeTime() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setClaimNotBeforeSeconds(5);
    MutableClock clock = new MutableClock(Instant.parse("2026-07-10T10:00:00Z"));
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    MembershipService service = new MembershipService(
        properties,
        quotaRepository,
        new InMemorySubscriptionRepository(),
        new InMemoryAbuseProtectionStore(clock),
        clock
    );
    String nonce = String.valueOf(service.createAdRewardSession(user("user_1"), "device_1").get("nonce"));

    ApiException wrongDevice = assertThrows(ApiException.class, () ->
        service.grantAdReward(user("user_1"), "device_2", "127.0.0.1", nonce)
    );
    ApiException early = assertThrows(ApiException.class, () ->
        service.grantAdReward(user("user_1"), "device_1", "127.0.0.1", nonce)
    );
    clock.advanceSeconds(5);
    Map<String, Object> granted = service.grantAdReward(user("user_1"), "device_1", "127.0.0.1", nonce);
    ApiException replay = assertThrows(ApiException.class, () ->
        service.grantAdReward(user("user_1"), "device_1", "127.0.0.1", nonce)
    );

    assertEquals("AD_REWARD_CLAIM_INVALID", wrongDevice.getCode());
    assertEquals("AD_REWARD_CLAIM_TOO_EARLY", early.getCode());
    assertEquals(1, granted.get("granted"));
    assertEquals("AD_REWARD_CLAIM_INVALID", replay.getCode());
  }

  @Test
  void shouldAllowFiveAdRewardGrantsThenRejectTheSixth() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setDailyMax(5);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    for (int view = 0; view < 5; view += 1) {
      settleAdReward(service, "user_1", "device_1", "127.0.0.1");
    }

    ApiException error = assertThrows(ApiException.class, () -> settleAdReward(service, "user_1", "device_1", "127.0.0.1"));
    assertEquals("AD_REWARD_DAILY_LIMIT_REACHED", error.getCode());
  }

  @Test
  void shouldNotGrantWhenAdRewardDisabled() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setEnabled(false);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    ApiException error = assertThrows(ApiException.class, () -> settleAdReward(service, "user_1", "device_1", "127.0.0.1"));
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
    assertEquals(5, entitlement.get("adRewardDailyLimit"));
    assertEquals(0, entitlement.get("adRewardDailyUsed"));
    assertEquals(50, entitlement.get("adRewardMaxCredits"));
    assertEquals(1, entitlement.get("adRewardGrantPerView"));
  }

  @Test
  void shouldRejectAnAdRewardThatWouldOnlyPartiallyFitTheCreditCapacity() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setMaxCredits(5);
    properties.getMembership().getAdReward().setGrantPerView(2);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();

    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));
    settleAdReward(service, "user_1", "device_1", "127.0.0.1");
    settleAdReward(service, "user_1", "device_1", "127.0.0.1");
    ApiException error = assertThrows(ApiException.class, () ->
        settleAdReward(service, "user_1", "device_1", "127.0.0.1")
    );

    Map<String, Object> entitlement = service.getEntitlement(user("user_1"));
    assertEquals("AD_REWARD_CREDIT_CAP_REACHED", error.getCode());
    assertEquals(4, entitlement.get("adRewardCredits"));
    assertEquals(2, entitlement.get("adRewardDailyUsed"));
  }

  @Test
  void shouldNotConsumeAnAdViewWhenStoredCreditIsAlreadyAtCapacity() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setMaxCredits(1);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 1, 1);
    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    ApiException error = assertThrows(ApiException.class, () ->
        settleAdReward(service, "user_1", "device_1", "127.0.0.1")
    );

    assertEquals("AD_REWARD_CREDIT_CAP_REACHED", error.getCode());
    Map<String, Object> entitlement = service.getEntitlement(user("user_1"));
    assertEquals(1, entitlement.get("adRewardCredits"));
    assertEquals(0, entitlement.get("adRewardDailyUsed"));
  }

  @Test
  void shouldRollBackStoredCreditWhenTheDeviceAdLimitRejectsTheReward() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setDailyMax(1);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    settleAdReward(service, "user_1", "device_1", "127.0.0.1");
    ApiException error = assertThrows(ApiException.class, () ->
        settleAdReward(service, "user_2", "device_1", "127.0.0.1")
    );

    assertEquals("AD_REWARD_DAILY_LIMIT", error.getCode());
    Map<String, Object> entitlement = service.getEntitlement(user("user_2"));
    assertEquals(0, entitlement.get("adRewardCredits"));
    assertEquals(0, entitlement.get("adRewardDailyUsed"));
  }

  @Test
  void shouldRollBackTheFullMultiCreditRewardWhenTheDeviceAdLimitRejectsIt() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setDailyMax(1);
    properties.getMembership().getAdReward().setGrantPerView(2);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    MembershipService service = serviceAt(properties, quotaRepository, Instant.parse("2026-07-10T10:00:00Z"));

    settleAdReward(service, "user_1", "device_1", "127.0.0.1");
    ApiException error = assertThrows(ApiException.class, () ->
        settleAdReward(service, "user_2", "device_1", "127.0.0.1")
    );

    assertEquals("AD_REWARD_DAILY_LIMIT", error.getCode());
    Map<String, Object> entitlement = service.getEntitlement(user("user_2"));
    assertEquals(0, entitlement.get("adRewardCredits"));
    assertEquals(0, entitlement.get("adRewardDailyUsed"));
  }

  @Test
  void shouldKeepRewardCreditsInvisibleUntilTheDeviceAllowanceIsReserved() {
    GaokaoProperties properties = newProperties();
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    InterleavingAbuseStore abuseStore = new InterleavingAbuseStore(quotaRepository, "user_1");
    MembershipService service = new MembershipService(
        properties,
        quotaRepository,
        new InMemorySubscriptionRepository(),
        abuseStore,
        Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC)
    );

    ApiException error = assertThrows(ApiException.class, () ->
        settleAdReward(service, "user_1", "device_1", "127.0.0.1")
    );

    assertEquals("AD_REWARD_DAILY_LIMIT", error.getCode());
    assertFalse(abuseStore.wasSpeculativeCreditConsumable());
    Map<String, Object> entitlement = service.getEntitlement(user("user_1"));
    assertEquals(0, entitlement.get("adRewardCredits"));
    assertEquals(0, entitlement.get("adRewardDailyUsed"));
  }

  @Test
  void shouldReleaseReservedCountersWhenTheCreditCapRejectsTheReward() {
    GaokaoProperties properties = newProperties();
    properties.getMembership().getAdReward().setDailyMax(1);
    properties.getMembership().getAdReward().setMaxCredits(1);
    InMemoryQuotaRepository quotaRepository = new InMemoryQuotaRepository();
    quotaRepository.grantCredits("user_1", "AD_REWARD_CREDITS", 1, 1);
    CountingAbuseStore abuseStore = new CountingAbuseStore();
    MembershipService service = new MembershipService(
        properties,
        quotaRepository,
        new InMemorySubscriptionRepository(),
        abuseStore,
        Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC)
    );

    ApiException error = assertThrows(ApiException.class, () ->
        settleAdReward(service, "user_1", "device_1", "127.0.0.1")
    );
    Map<String, Object> secondGrant = settleAdReward(service, "user_2", "device_1", "127.0.0.1");

    assertEquals("AD_REWARD_CREDIT_CAP_REACHED", error.getCode());
    assertEquals(2, abuseStore.acceptedCount());
    assertEquals(1, abuseStore.releaseCount());
    assertEquals(1, secondGrant.get("granted"));
    assertEquals(0, service.getEntitlement(user("user_1")).get("adRewardDailyUsed"));
  }

  private GaokaoProperties newProperties() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getMembership().setTrialTotalLimit(0);
    properties.getMembership().setTrialDailyLimit(5);
    properties.getMembership().setDeviceDailyLimit(0);
    properties.getMembership().setIpDailyLimit(0);
    properties.getMembership().getAdReward().setEnabled(true);
    properties.getMembership().getAdReward().setGrantPerView(1);
    properties.getMembership().getAdReward().setDailyMax(5);
    properties.getMembership().getAdReward().setMaxCredits(50);
    properties.getMembership().getAdReward().setClaimNotBeforeSeconds(0);
    return properties;
  }

  private MembershipService serviceAt(
      GaokaoProperties properties,
      InMemoryQuotaRepository quotaRepository,
      Instant instant
  ) {
    Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
    return new MembershipService(
        properties,
        quotaRepository,
        new InMemorySubscriptionRepository(),
        new InMemoryAbuseProtectionStore(clock),
        clock
    );
  }

  private AuthenticatedUser user(String userId) {
    Instant now = Instant.parse("2026-07-10T10:00:00Z");
    return new AuthenticatedUser(userId, "open_" + userId, now, now.plusSeconds(3600));
  }

  private Map<String, Object> settleAdReward(MembershipService service, String userId, String deviceId, String clientIp) {
    Map<String, Object> session = service.createAdRewardSession(user(userId), deviceId);
    return service.grantAdReward(user(userId), deviceId, clientIp, String.valueOf(session.get("nonce")));
  }

  private void exhaustDailyFreeQuota(InMemoryQuotaRepository quotaRepository, String userId, int limit) {
    for (int attempt = 0; attempt < limit; attempt += 1) {
      assertTrue(quotaRepository.tryConsume(userId, "ESSAY_DAY_2026-07-10", limit));
    }
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
          userId, quotaType,
          Math.max(current.usedCount() - Math.max(amount, 0), 0),
          current.limitCount(), Instant.now()
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

  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    private void advanceSeconds(long seconds) {
      now = now.plusSeconds(seconds);
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

  private static final class InterleavingAbuseStore implements AbuseProtectionStore {
    private final InMemoryQuotaRepository quotaRepository;
    private final String userId;
    private final InMemoryAbuseProtectionStore claims = new InMemoryAbuseProtectionStore(
        Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC)
    );
    private boolean speculativeCreditConsumable;

    private InterleavingAbuseStore(InMemoryQuotaRepository quotaRepository, String userId) {
      this.quotaRepository = quotaRepository;
      this.userId = userId;
    }

    @Override
    public boolean tryConsume(String key, int limit, Duration ttl) {
      speculativeCreditConsumable = quotaRepository.consumeCredit(userId, "AD_REWARD_CREDITS");
      return false;
    }

    @Override
    public void release(String key) {
    }

    @Override
    public void putChallenge(String tokenHash, String subjectHash, Duration ttl) {
    }

    @Override
    public boolean consumeChallenge(String tokenHash, String subjectHash) {
      return false;
    }

    @Override
    public void putOneTimeClaim(String claimHash, String subjectHash, Instant notBefore, Duration ttl) {
      claims.putOneTimeClaim(claimHash, subjectHash, notBefore, ttl);
    }

    @Override
    public OneTimeClaimConsumption consumeOneTimeClaim(String claimHash, String subjectHash, Instant now) {
      return claims.consumeOneTimeClaim(claimHash, subjectHash, now);
    }

    @Override
    public boolean isPersistent() {
      return false;
    }

    private boolean wasSpeculativeCreditConsumable() {
      return speculativeCreditConsumable;
    }
  }

  private static final class CountingAbuseStore implements AbuseProtectionStore {
    private int accepted;
    private int released;
    private final InMemoryAbuseProtectionStore claims = new InMemoryAbuseProtectionStore(
        Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC)
    );

    @Override
    public boolean tryConsume(String key, int limit, Duration ttl) {
      accepted += 1;
      return true;
    }

    @Override
    public void release(String key) {
      released += 1;
    }

    @Override
    public void putChallenge(String tokenHash, String subjectHash, Duration ttl) {
    }

    @Override
    public boolean consumeChallenge(String tokenHash, String subjectHash) {
      return false;
    }

    @Override
    public void putOneTimeClaim(String claimHash, String subjectHash, Instant notBefore, Duration ttl) {
      claims.putOneTimeClaim(claimHash, subjectHash, notBefore, ttl);
    }

    @Override
    public OneTimeClaimConsumption consumeOneTimeClaim(String claimHash, String subjectHash, Instant now) {
      return claims.consumeOneTimeClaim(claimHash, subjectHash, now);
    }

    @Override
    public boolean isPersistent() {
      return false;
    }

    private int acceptedCount() {
      return accepted;
    }

    private int releaseCount() {
      return released;
    }
  }
}
