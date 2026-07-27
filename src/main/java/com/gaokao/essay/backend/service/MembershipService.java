package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.UserEntitlementSnapshot;
import com.gaokao.essay.backend.model.UserSubscription;
import com.gaokao.essay.backend.model.UserUsageQuota;
import com.gaokao.essay.backend.repository.UserSubscriptionRepository;
import com.gaokao.essay.backend.repository.UserUsageQuotaRepository;
import com.gaokao.essay.backend.security.AbuseProtectionStore;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {

  private final GaokaoProperties properties;
  private final UserUsageQuotaRepository userUsageQuotaRepository;
  private final UserSubscriptionRepository userSubscriptionRepository;
  private final Clock clock;
  private final AbuseProtectionStore abuseProtectionStore;

  @Autowired
  public MembershipService(
      GaokaoProperties properties,
      UserUsageQuotaRepository userUsageQuotaRepository,
      UserSubscriptionRepository userSubscriptionRepository,
      AbuseProtectionStore abuseProtectionStore
  ) {
    this(properties, userUsageQuotaRepository, userSubscriptionRepository, abuseProtectionStore, Clock.systemUTC());
  }

  MembershipService(
      GaokaoProperties properties,
      UserUsageQuotaRepository userUsageQuotaRepository,
      UserSubscriptionRepository userSubscriptionRepository,
      AbuseProtectionStore abuseProtectionStore,
      Clock clock
  ) {
    this.properties = properties;
    this.userUsageQuotaRepository = userUsageQuotaRepository;
    this.userSubscriptionRepository = userSubscriptionRepository;
    this.abuseProtectionStore = abuseProtectionStore;
    this.clock = clock;
  }

  public UsageReservation reserveEssayAccess(AuthenticatedUser user) {
    return reserveEssayAccess(user, "", "");
  }

  public UsageReservation reserveEssayAccess(AuthenticatedUser user, String deviceId, String clientIp) {
    Instant now = clock.instant();
    Optional<UserSubscription> subscription = userSubscriptionRepository.findByUserId(user.userId());
    if (subscription.filter(item -> item.isActiveAt(now)).isPresent()) {
      return UsageReservation.subscription(user.userId());
    }

    List<String> consumedQuotaTypes = new ArrayList<>();
    if (!tryConsumeTrialQuota(user.userId(), buildTotalTrialQuotaType(), resolveTrialLimit(), consumedQuotaTypes)) {
      if (tryConsumeAdRewardCredit(user.userId(), consumedQuotaTypes)) {
        return finalizeAdRewardReservation(user, deviceId, clientIp, consumedQuotaTypes, now);
      }
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TRIAL_LIMIT_REACHED", "免费体验次数已用完，看广告可继续获得批改次数");
    }

    int dailyLimit = resolveTrialDailyLimit();
    if (!tryConsumeTrialQuota(user.userId(), buildDailyTrialQuotaType(now), dailyLimit, consumedQuotaTypes)) {
      rollbackConsumedQuotas(user.userId(), consumedQuotaTypes);
      if (tryConsumeAdRewardCredit(user.userId(), consumedQuotaTypes)) {
        return finalizeAdRewardReservation(user, deviceId, clientIp, consumedQuotaTypes, now);
      }
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TRIAL_DAILY_LIMIT_REACHED", "今日免费体验次数已用完，看广告可继续获得批改次数");
    }

    List<String> abuseKeys = new ArrayList<>();
    try {
      consumeExternalDailyQuota(
          "device-day",
          deviceId,
          properties.getMembership().getDeviceDailyLimit(),
          "DEVICE_DAILY_LIMIT_REACHED",
          "当前设备今日免费次数已用完",
          abuseKeys,
          now
      );
      consumeExternalDailyQuota(
          "ip-day",
          clientIp,
          properties.getMembership().getIpDailyLimit(),
          "IP_DAILY_LIMIT_REACHED",
          "当前网络今日免费次数已用完",
          abuseKeys,
          now
      );
    } catch (RuntimeException error) {
      rollbackConsumedQuotas(user.userId(), consumedQuotaTypes);
      rollbackAbuseKeys(abuseKeys);
      throw error;
    }

    return UsageReservation.trial(user.userId(), consumedQuotaTypes, abuseKeys);
  }

  private UsageReservation finalizeAdRewardReservation(
      AuthenticatedUser user,
      String deviceId,
      String clientIp,
      List<String> consumedQuotaTypes,
      Instant now
  ) {
    List<String> abuseKeys = new ArrayList<>();
    try {
      consumeExternalDailyQuota(
          "device-day",
          deviceId,
          properties.getMembership().getDeviceDailyLimit(),
          "DEVICE_DAILY_LIMIT_REACHED",
          "当前设备今日次数已用完",
          abuseKeys,
          now
      );
      consumeExternalDailyQuota(
          "ip-day",
          clientIp,
          properties.getMembership().getIpDailyLimit(),
          "IP_DAILY_LIMIT_REACHED",
          "当前网络今日次数已用完",
          abuseKeys,
          now
      );
    } catch (RuntimeException error) {
      rollbackConsumedQuotas(user.userId(), consumedQuotaTypes);
      rollbackAbuseKeys(abuseKeys);
      throw error;
    }
    return UsageReservation.trial(user.userId(), consumedQuotaTypes, abuseKeys);
  }

  private boolean tryConsumeAdRewardCredit(String userId, List<String> consumedQuotaTypes) {
    if (!properties.getMembership().getAdReward().isEnabled()) {
      return false;
    }
    if (userUsageQuotaRepository.consumeCredit(userId, buildAdRewardQuotaType())) {
      consumedQuotaTypes.add(buildAdRewardQuotaType());
      return true;
    }
    return false;
  }

  public void releaseReservation(UsageReservation reservation) {
    if (reservation == null || !reservation.countedTrial()) {
      return;
    }
    rollbackConsumedQuotas(reservation.userId(), reservation.quotaTypes());
    rollbackAbuseKeys(reservation.abuseKeys());
  }

  public Map<String, Object> grantAdReward(AuthenticatedUser user, String deviceId, String clientIp) {
    Instant now = clock.instant();
    GaokaoProperties.AdReward adRewardConfig = properties.getMembership().getAdReward();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("enabled", adRewardConfig.isEnabled());

    if (!adRewardConfig.isEnabled()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "AD_REWARD_DISABLED", "看广告得次数功能未开启");
    }

    int dailyMax = adRewardConfig.getDailyMax();
    if (dailyMax > 0) {
      String dailyKey = buildAdRewardDailyKey(deviceId, clientIp, now);
      Duration dailyTtl = Duration.between(now, nextQuotaResetAt(now));
      if (!abuseProtectionStore.tryConsume(dailyKey, dailyMax, dailyTtl)) {
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AD_REWARD_DAILY_LIMIT", "今日看广告次数已达上限");
      }
    }

    int grantAmount = adRewardConfig.getGrantPerView();
    int maxCredits = adRewardConfig.getMaxCredits();
    userUsageQuotaRepository.grantCredits(user.userId(), buildAdRewardQuotaType(), grantAmount, maxCredits);

    UserUsageQuota creditQuota = userUsageQuotaRepository
        .findByUserIdAndQuotaType(user.userId(), buildAdRewardQuotaType())
        .orElse(new UserUsageQuota(user.userId(), buildAdRewardQuotaType(), 0, maxCredits, now));

    result.put("granted", grantAmount);
    result.put("adRewardCredits", Math.max(creditQuota.usedCount(), 0));
    result.put("adRewardMaxCredits", maxCredits);
    return result;
  }

  public Map<String, Object> getEntitlement(AuthenticatedUser user) {
    Instant now = clock.instant();
    Map<String, Object> data = toMap(buildSnapshot(user.userId(), now));
    data.put("adRewardEnabled", properties.getMembership().getAdReward().isEnabled());
    data.put("adRewardCredits", resolveAdRewardCredits(user.userId(), now));
    data.put("adRewardMaxCredits", properties.getMembership().getAdReward().getMaxCredits());
    data.put("adRewardGrantPerView", properties.getMembership().getAdReward().getGrantPerView());
    return data;
  }

  public List<Map<String, Object>> getPlans() {
    return List.of(
        plan(properties.getMembership().getMonthly()),
        plan(properties.getMembership().getYearly())
    );
  }

  public GaokaoProperties.Plan requirePlan(String planCode) {
    return resolvePlan(planCode);
  }

  public Map<String, Object> activateDebugSubscription(AuthenticatedUser user, String planCode, boolean autoRenew) {
    if (!properties.getMembership().isAllowDebugSubscriptionActivate()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "BILLING_DISABLED", "当前环境未开放联调会员开通接口");
    }

    GaokaoProperties.Plan plan = resolvePlan(planCode);
    Instant now = clock.instant();
    UserSubscription subscription = new UserSubscription(
        user.userId(),
        plan.getCode(),
        plan.getName(),
        "ACTIVE",
        now,
        now.plus(Math.max(plan.getDurationDays(), 1), ChronoUnit.DAYS),
        autoRenew,
        "debug",
        "debug-" + plan.getCode(),
        now
    );
    userSubscriptionRepository.save(subscription);
    return toMap(buildSnapshot(user.userId(), now));
  }

  public UserSubscription activatePaidSubscription(
      String userId,
      String planCode,
      String provider,
      String providerReference,
      Instant activatedAt
  ) {
    GaokaoProperties.Plan plan = resolvePlan(planCode);
    Optional<UserSubscription> existing = userSubscriptionRepository.findByUserId(userId);
    if (existing.filter(item ->
        item.isActiveAt(activatedAt)
            && TextUtils.lower(item.provider()).equals(TextUtils.lower(provider))
            && TextUtils.lower(item.providerReference()).equals(TextUtils.lower(providerReference))
    ).isPresent()) {
      return existing.get();
    }

    Instant baseInstant = existing
        .filter(item -> item.isActiveAt(activatedAt) && item.expiresAt() != null && item.expiresAt().isAfter(activatedAt))
        .map(UserSubscription::expiresAt)
        .orElse(activatedAt);

    UserSubscription subscription = new UserSubscription(
        userId,
        plan.getCode(),
        plan.getName(),
        "ACTIVE",
        activatedAt,
        baseInstant.plus(Math.max(plan.getDurationDays(), 1), ChronoUnit.DAYS),
        false,
        provider,
        providerReference,
        activatedAt
    );
    userSubscriptionRepository.save(subscription);
    return subscription;
  }

  private Map<String, Object> toMap(UserEntitlementSnapshot snapshot) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("subscriptionActive", snapshot.subscriptionActive());
    data.put("accessMode", snapshot.accessMode());
    data.put("trialPolicy", snapshot.trialPolicy());
    data.put("trialLimit", snapshot.trialLimit());
    data.put("trialTotalLimit", snapshot.trialTotalLimit());
    data.put("trialDailyLimit", snapshot.trialDailyLimit());
    data.put("trialUsed", snapshot.trialUsed());
    data.put("trialRemaining", snapshot.trialRemaining());
    data.put("trialTotalUsed", snapshot.trialTotalUsed());
    data.put("trialTotalRemaining", snapshot.trialTotalRemaining());
    data.put("trialDailyUsed", snapshot.trialDailyUsed());
    data.put("trialDailyRemaining", snapshot.trialDailyRemaining());
    data.put("trialResetAt", snapshot.trialResetAt() == null ? "" : snapshot.trialResetAt().toString());
    data.put("subscriptionStatus", snapshot.subscriptionStatus());
    data.put("subscriptionPlanCode", snapshot.subscriptionPlanCode());
    data.put("subscriptionPlanName", snapshot.subscriptionPlanName());
    data.put("subscriptionStartedAt", snapshot.subscriptionStartedAt() == null ? "" : snapshot.subscriptionStartedAt().toString());
    data.put("subscriptionExpiresAt", snapshot.subscriptionExpiresAt() == null ? "" : snapshot.subscriptionExpiresAt().toString());
    data.put("subscriptionAutoRenew", snapshot.subscriptionAutoRenew());
    data.put("subscriptionProvider", snapshot.subscriptionProvider());
    data.put("serverTime", snapshot.serverTime().toString());
    return data;
  }

  private int resolveAdRewardCredits(String userId, Instant now) {
    if (!properties.getMembership().getAdReward().isEnabled()) {
      return 0;
    }
    return userUsageQuotaRepository
        .findByUserIdAndQuotaType(userId, buildAdRewardQuotaType())
        .map(quota -> Math.max(quota.usedCount(), 0))
        .orElse(0);
  }

  private UserEntitlementSnapshot buildSnapshot(String userId, Instant now) {
    String totalQuotaType = buildTotalTrialQuotaType();
    int totalLimit = resolveTrialLimit();
    UserUsageQuota totalQuota = userUsageQuotaRepository.findByUserIdAndQuotaType(userId, totalQuotaType)
        .orElse(new UserUsageQuota(userId, totalQuotaType, 0, totalLimit, now));
    int dailyLimit = resolveTrialDailyLimit();
    UserUsageQuota dailyQuota = userUsageQuotaRepository.findByUserIdAndQuotaType(userId, buildDailyTrialQuotaType(now))
        .orElse(new UserUsageQuota(userId, buildDailyTrialQuotaType(now), 0, dailyLimit, now));
    Optional<UserSubscription> subscription = userSubscriptionRepository.findByUserId(userId);
    boolean active = subscription.filter(item -> item.isActiveAt(now)).isPresent();
    int totalUsed = Math.max(totalQuota.usedCount(), 0);
    int totalRemaining = active ? totalLimit : Math.max(totalLimit - totalUsed, 0);
    int normalizedDailyLimit = Math.max(dailyLimit, 0);
    int dailyUsed = normalizedDailyLimit <= 0 ? 0 : Math.max(dailyQuota.usedCount(), 0);
    int dailyRemaining = active
        ? normalizedDailyLimit
        : (normalizedDailyLimit <= 0 ? 0 : Math.max(normalizedDailyLimit - dailyUsed, 0));
    int effectiveRemaining = normalizedDailyLimit > 0
        ? Math.min(totalRemaining, dailyRemaining)
        : totalRemaining;

    return new UserEntitlementSnapshot(
        active,
        active ? "subscription" : "trial",
        normalizedDailyLimit > 0 ? "total+daily" : "total",
        normalizedDailyLimit > 0 ? Math.min(totalLimit, normalizedDailyLimit) : totalLimit,
        normalizedDailyLimit > 0 ? Math.max(totalLimit - totalRemaining, dailyUsed) : totalUsed,
        effectiveRemaining,
        totalLimit,
        totalUsed,
        totalRemaining,
        normalizedDailyLimit,
        dailyUsed,
        dailyRemaining,
        normalizedDailyLimit > 0 ? nextQuotaResetAt(now) : null,
        subscription.map(item -> item.effectiveStatus(now)).orElse("INACTIVE"),
        subscription.map(UserSubscription::planCode).orElse(""),
        subscription.map(UserSubscription::planName).orElse(""),
        subscription.map(UserSubscription::startedAt).orElse(null),
        subscription.map(UserSubscription::expiresAt).orElse(null),
        subscription.map(UserSubscription::autoRenew).orElse(false),
        subscription.map(UserSubscription::provider).orElse(""),
        now
    );
  }

  private Map<String, Object> plan(GaokaoProperties.Plan plan) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("planCode", plan.getCode());
    data.put("planName", plan.getName());
    data.put("description", plan.getDescription());
    data.put("durationDays", plan.getDurationDays());
    data.put("priceFen", plan.getPriceFen());
    data.put("priceText", String.format("¥%.2f", plan.getPriceFen() / 100.0));
    data.put("recommended", plan.isRecommended());
    return data;
  }

  private GaokaoProperties.Plan resolvePlan(String planCode) {
    String normalized = TextUtils.lower(planCode);
    if (normalized.equals(TextUtils.lower(properties.getMembership().getMonthly().getCode()))) {
      return properties.getMembership().getMonthly();
    }
    if (normalized.equals(TextUtils.lower(properties.getMembership().getYearly().getCode()))) {
      return properties.getMembership().getYearly();
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_NOT_FOUND", "未找到对应套餐");
  }

  private ZoneId resolveQuotaZoneId() {
    String zoneId = properties.getMembership().getQuotaZoneId();
    if (TextUtils.isBlank(zoneId)) {
      return ZoneId.of("Asia/Shanghai");
    }
    return ZoneId.of(zoneId);
  }

  private String buildTotalTrialQuotaType() {
    return "ESSAY_TOTAL";
  }

  private String buildDailyTrialQuotaType(Instant now) {
    LocalDate localDate = LocalDate.ofInstant(now, resolveQuotaZoneId());
    return "ESSAY_DAY_" + localDate;
  }

  private String buildAdRewardQuotaType() {
    return "AD_REWARD_CREDITS";
  }

  private String buildAdRewardDailyKey(String deviceId, String clientIp, Instant now) {
    String subject = !TextUtils.isBlank(deviceId) ? deviceId : (!TextUtils.isBlank(clientIp) ? clientIp : "anon");
    String date = LocalDate.ofInstant(now, resolveQuotaZoneId()).toString();
    return "ad-reward-day:" + date + ":" + TextUtils.sha256(subject).substring(0, 32);
  }

  private int resolveTrialLimit() {
    return Math.max(properties.getMembership().getTrialTotalLimit(), 1);
  }

  private int resolveTrialDailyLimit() {
    return Math.max(properties.getMembership().getTrialDailyLimit(), 0);
  }

  private boolean tryConsumeTrialQuota(String userId, String quotaType, int limit, List<String> consumedQuotaTypes) {
    if (limit <= 0) {
      return true;
    }
    boolean consumed = userUsageQuotaRepository.tryConsume(userId, quotaType, limit);
    if (consumed) {
      consumedQuotaTypes.add(quotaType);
    }
    return consumed;
  }

  private void rollbackConsumedQuotas(String userId, List<String> quotaTypes) {
    for (String quotaType : quotaTypes) {
      userUsageQuotaRepository.release(userId, quotaType);
    }
  }

  private void consumeExternalDailyQuota(
      String scope,
      String subject,
      int limit,
      String errorCode,
      String message,
      List<String> consumedKeys,
      Instant now
  ) {
    if (TextUtils.isBlank(subject) || limit <= 0) {
      return;
    }
    String date = LocalDate.ofInstant(now, resolveQuotaZoneId()).toString();
    String key = scope + ":" + date + ":" + TextUtils.sha256(subject).substring(0, 32);
    Duration ttl = Duration.between(now, nextQuotaResetAt(now));
    if (!abuseProtectionStore.tryConsume(key, limit, ttl)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, errorCode, message);
    }
    consumedKeys.add(key);
  }

  private void rollbackAbuseKeys(List<String> keys) {
    keys.forEach(abuseProtectionStore::release);
  }

  private Instant nextQuotaResetAt(Instant now) {
    return LocalDate.ofInstant(now, resolveQuotaZoneId())
        .plusDays(1)
        .atStartOfDay(resolveQuotaZoneId())
        .toInstant();
  }

  public record UsageReservation(
      String userId,
      List<String> quotaTypes,
      List<String> abuseKeys,
      boolean countedTrial
  ) {
    public static UsageReservation subscription(String userId) {
      return new UsageReservation(userId, List.of(), List.of(), false);
    }

    public static UsageReservation trial(String userId, List<String> quotaTypes) {
      return trial(userId, quotaTypes, List.of());
    }

    public static UsageReservation trial(String userId, List<String> quotaTypes, List<String> abuseKeys) {
      return new UsageReservation(userId, List.copyOf(quotaTypes), List.copyOf(abuseKeys), true);
    }
  }
}
