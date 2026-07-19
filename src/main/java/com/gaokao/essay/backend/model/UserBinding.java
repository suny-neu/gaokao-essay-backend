package com.gaokao.essay.backend.model;

import java.time.Instant;

public record UserBinding(
    String userId,
    String openId,
    Instant createdAt,
    Instant lastLoginAt
) {
}
