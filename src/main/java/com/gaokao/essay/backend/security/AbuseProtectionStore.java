package com.gaokao.essay.backend.security;

import java.time.Duration;
import java.time.Instant;

public interface AbuseProtectionStore {
  boolean tryConsume(String key, int limit, Duration ttl);

  void release(String key);

  void putChallenge(String tokenHash, String subjectHash, Duration ttl);

  boolean consumeChallenge(String tokenHash, String subjectHash);

  default void putOneTimeClaim(String claimHash, String subjectHash, Instant notBefore, Duration ttl) {
    throw new UnsupportedOperationException("One-time claims are not configured");
  }

  default OneTimeClaimConsumption consumeOneTimeClaim(String claimHash, String subjectHash, Instant now) {
    return OneTimeClaimConsumption.MISSING_OR_MISMATCH;
  }

  boolean isPersistent();

  enum OneTimeClaimConsumption {
    ACCEPTED,
    TOO_EARLY,
    MISSING_OR_MISMATCH
  }
}
