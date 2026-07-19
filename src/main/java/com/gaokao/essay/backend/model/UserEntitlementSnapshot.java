package com.gaokao.essay.backend.model;

import java.time.Instant;

public record UserEntitlementSnapshot(
    boolean subscriptionActive,
    String accessMode,
    String trialPolicy,
    int trialLimit,
    int trialUsed,
    int trialRemaining,
    int trialTotalLimit,
    int trialTotalUsed,
    int trialTotalRemaining,
    int trialDailyLimit,
    int trialDailyUsed,
    int trialDailyRemaining,
    Instant trialResetAt,
    String subscriptionStatus,
    String subscriptionPlanCode,
    String subscriptionPlanName,
    Instant subscriptionStartedAt,
    Instant subscriptionExpiresAt,
    boolean subscriptionAutoRenew,
    String subscriptionProvider,
    Instant serverTime
) {
}
