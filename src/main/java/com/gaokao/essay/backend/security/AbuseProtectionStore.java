package com.gaokao.essay.backend.security;

import java.time.Duration;

public interface AbuseProtectionStore {
  boolean tryConsume(String key, int limit, Duration ttl);

  void release(String key);

  void putChallenge(String tokenHash, String subjectHash, Duration ttl);

  boolean consumeChallenge(String tokenHash, String subjectHash);

  boolean isPersistent();
}
