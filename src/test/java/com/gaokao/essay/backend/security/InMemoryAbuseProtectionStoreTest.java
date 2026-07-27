package com.gaokao.essay.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryAbuseProtectionStoreTest {
  @Test
  void enforcesAndReleasesCounters() {
    InMemoryAbuseProtectionStore store = new InMemoryAbuseProtectionStore();

    assertThat(store.tryConsume("user:a", 2, Duration.ofMinutes(1))).isTrue();
    assertThat(store.tryConsume("user:a", 2, Duration.ofMinutes(1))).isTrue();
    assertThat(store.tryConsume("user:a", 2, Duration.ofMinutes(1))).isFalse();
    store.release("user:a");
    assertThat(store.tryConsume("user:a", 2, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void consumesChallengeOnlyOnceAndOnlyForOwner() {
    InMemoryAbuseProtectionStore store = new InMemoryAbuseProtectionStore();
    store.putChallenge("token", "user-a", Duration.ofMinutes(1));

    assertThat(store.consumeChallenge("token", "user-b")).isFalse();
    assertThat(store.consumeChallenge("token", "user-a")).isFalse();

    store.putChallenge("token-2", "user-a", Duration.ofMinutes(1));
    assertThat(store.consumeChallenge("token-2", "user-a")).isTrue();
    assertThat(store.consumeChallenge("token-2", "user-a")).isFalse();
  }
}
