package com.gaokao.essay.backend.model;

import java.time.Instant;

public record AuthenticatedUser(
    String userId,
    String openId,
    Instant issuedAt,
    Instant expiresAt
) {
}
