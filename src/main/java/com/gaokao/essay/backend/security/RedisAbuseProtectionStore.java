package com.gaokao.essay.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "gaokao.security.redis-required", havingValue = "true")
public class RedisAbuseProtectionStore implements AbuseProtectionStore {
  private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
      "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[2]); end; "
          + "if n>tonumber(ARGV[1]) then redis.call('DECR',KEYS[1]); return 0; end; return 1;",
      Long.class
  );
  private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
      "local n=tonumber(redis.call('GET',KEYS[1]) or '0'); "
          + "if n<=1 then redis.call('DEL',KEYS[1]); return 0; end; return redis.call('DECR',KEYS[1]);",
      Long.class
  );
  private static final DefaultRedisScript<Long> CONSUME_CHALLENGE = new DefaultRedisScript<>(
      "local v=redis.call('GET',KEYS[1]); if not v or v~=ARGV[1] then return 0; end; "
          + "redis.call('DEL',KEYS[1]); return 1;",
      Long.class
  );
  private static final DefaultRedisScript<Long> CONSUME_ONE_TIME_CLAIM = new DefaultRedisScript<>(
      "local v=redis.call('GET',KEYS[1]); if not v then return 0; end; "
          + "local p=string.find(v,'|'); if not p or string.sub(v,1,p-1)~=ARGV[1] then return 0; end; "
          + "if tonumber(ARGV[2])<tonumber(string.sub(v,p+1)) then return -1; end; "
          + "redis.call('DEL',KEYS[1]); return 1;",
      Long.class
  );

  private final StringRedisTemplate redis;

  public RedisAbuseProtectionStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean tryConsume(String key, int limit, Duration ttl) {
    if (limit <= 0) {
      return true;
    }
    Long result = redis.execute(
        CONSUME,
        List.of(prefixed(key)),
        String.valueOf(limit),
        String.valueOf(Math.max(ttl.toMillis(), 1000))
    );
    return Long.valueOf(1).equals(result);
  }

  @Override
  public void release(String key) {
    redis.execute(RELEASE, List.of(prefixed(key)));
  }

  @Override
  public void putChallenge(String tokenHash, String subjectHash, Duration ttl) {
    redis.opsForValue().set(prefixed("challenge:" + tokenHash), subjectHash, ttl);
  }

  @Override
  public boolean consumeChallenge(String tokenHash, String subjectHash) {
    Long result = redis.execute(
        CONSUME_CHALLENGE,
        List.of(prefixed("challenge:" + tokenHash)),
        subjectHash
    );
    return Long.valueOf(1).equals(result);
  }

  @Override
  public void putOneTimeClaim(String claimHash, String subjectHash, Instant notBefore, Duration ttl) {
    redis.opsForValue().set(
        prefixed("claim:" + claimHash),
        subjectHash + "|" + notBefore.toEpochMilli(),
        ttl
    );
  }

  @Override
  public OneTimeClaimConsumption consumeOneTimeClaim(String claimHash, String subjectHash, Instant now) {
    Long result = redis.execute(
        CONSUME_ONE_TIME_CLAIM,
        List.of(prefixed("claim:" + claimHash)),
        subjectHash,
        String.valueOf(now.toEpochMilli())
    );
    if (Long.valueOf(1).equals(result)) {
      return OneTimeClaimConsumption.ACCEPTED;
    }
    return Long.valueOf(-1).equals(result)
        ? OneTimeClaimConsumption.TOO_EARLY
        : OneTimeClaimConsumption.MISSING_OR_MISMATCH;
  }

  @Override
  public boolean isPersistent() {
    try {
      String response = redis.getConnectionFactory().getConnection().ping();
      return "PONG".equalsIgnoreCase(response);
    } catch (RuntimeException error) {
      return false;
    }
  }

  private String prefixed(String key) {
    return "gaokao:abuse:" + key;
  }
}
