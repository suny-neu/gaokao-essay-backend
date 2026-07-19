package com.gaokao.essay.backend.model;

import java.time.Instant;

public record UserSubscription(
    String userId,
    String planCode,
    String planName,
    String status,
    Instant startedAt,
    Instant expiresAt,
    boolean autoRenew,
    String provider,
    String providerReference,
    Instant updatedAt
) {

  public boolean isActiveAt(Instant now) {
    return "ACTIVE".equalsIgnoreCase(status) && (expiresAt == null || expiresAt.isAfter(now));
  }

  public String effectiveStatus(Instant now) {
    if (isActiveAt(now)) {
      return "ACTIVE";
    }
    if ("ACTIVE".equalsIgnoreCase(status) && expiresAt != null && !expiresAt.isAfter(now)) {
      return "EXPIRED";
    }
    return status == null || status.isBlank() ? "INACTIVE" : status;
  }
}
