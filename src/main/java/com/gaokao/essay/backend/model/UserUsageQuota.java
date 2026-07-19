package com.gaokao.essay.backend.model;

import java.time.Instant;

public record UserUsageQuota(
    String userId,
    String quotaType,
    int usedCount,
    int limitCount,
    Instant updatedAt
) {
}
