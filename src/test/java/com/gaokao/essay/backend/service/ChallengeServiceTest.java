package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.security.InMemoryAbuseProtectionStore;
import org.junit.jupiter.api.Test;

class ChallengeServiceTest {
  @Test
  void challengeIsBoundToUserAndCanOnlyBeUsedOnce() {
    ChallengeService service = new ChallengeService(
        new GaokaoProperties(),
        new InMemoryAbuseProtectionStore()
    );
    String token = service.issueChallenge("user-a");
    ApiException mismatch = assertThrows(
        ApiException.class,
        () -> service.consumeChallenge(token, "user-b")
    );
    assertEquals("CHALLENGE_ALREADY_USED", mismatch.getCode());

    String second = service.issueChallenge("user-a");
    service.consumeChallenge(second, "user-a");
    ApiException replay = assertThrows(
        ApiException.class,
        () -> service.consumeChallenge(second, "user-a")
    );
    assertEquals("CHALLENGE_ALREADY_USED", replay.getCode());
  }
}
