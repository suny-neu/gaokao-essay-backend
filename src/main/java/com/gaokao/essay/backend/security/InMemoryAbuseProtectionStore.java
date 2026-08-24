package com.gaokao.essay.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "gaokao.security.redis-required",
    havingValue = "false",
    matchIfMissing = true
)
public class InMemoryAbuseProtectionStore implements AbuseProtectionStore {
  private final Map<String, Counter> counters = new ConcurrentHashMap<>();
  private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
  private final Map<String, OneTimeClaim> oneTimeClaims = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryAbuseProtectionStore() {
    this(Clock.systemUTC());
  }

  public InMemoryAbuseProtectionStore(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean tryConsume(String key, int limit, Duration ttl) {
    if (limit <= 0) {
      return true;
    }
    long now = clock.millis();
    AtomicBoolean accepted = new AtomicBoolean(false);
    counters.compute(key, (ignored, current) -> {
      if (current == null || current.expiresAt <= now) {
        accepted.set(true);
        return new Counter(1, now + ttl.toMillis());
      }
      if (current.count >= limit) {
        return current;
      }
      accepted.set(true);
      return new Counter(current.count + 1, current.expiresAt);
    });
    return accepted.get();
  }

  @Override
  public void release(String key) {
    counters.computeIfPresent(key, (ignored, current) ->
        current.count <= 1 ? null : new Counter(current.count - 1, current.expiresAt));
  }

  @Override
  public void putChallenge(String tokenHash, String subjectHash, Duration ttl) {
    challenges.put(tokenHash, new Challenge(subjectHash, clock.millis() + ttl.toMillis()));
  }

  @Override
  public boolean consumeChallenge(String tokenHash, String subjectHash) {
    Challenge challenge = challenges.remove(tokenHash);
    return challenge != null
        && challenge.expiresAt >= clock.millis()
        && challenge.subjectHash.equals(subjectHash);
  }

  @Override
  public void putOneTimeClaim(String claimHash, String subjectHash, Instant notBefore, Duration ttl) {
    long now = clock.millis();
    oneTimeClaims.put(claimHash, new OneTimeClaim(
        subjectHash,
        Math.max(notBefore.toEpochMilli(), now),
        now + Math.max(ttl.toMillis(), 1000)
    ));
  }

  @Override
  public OneTimeClaimConsumption consumeOneTimeClaim(String claimHash, String subjectHash, Instant now) {
    AtomicBoolean accepted = new AtomicBoolean(false);
    AtomicBoolean early = new AtomicBoolean(false);
    long nowMillis = now.toEpochMilli();
    oneTimeClaims.compute(claimHash, (ignored, current) -> {
      if (current == null || current.expiresAt < nowMillis) {
        return null;
      }
      if (!current.subjectHash.equals(subjectHash)) {
        return current;
      }
      if (nowMillis < current.notBeforeAt) {
        early.set(true);
        return current;
      }
      accepted.set(true);
      return null;
    });
    if (accepted.get()) {
      return OneTimeClaimConsumption.ACCEPTED;
    }
    return early.get() ? OneTimeClaimConsumption.TOO_EARLY : OneTimeClaimConsumption.MISSING_OR_MISMATCH;
  }

  @Override
  public boolean isPersistent() {
    return false;
  }

  private record Counter(int count, long expiresAt) {
  }

  private record Challenge(String subjectHash, long expiresAt) {
  }

  private record OneTimeClaim(String subjectHash, long notBeforeAt, long expiresAt) {
  }
}
