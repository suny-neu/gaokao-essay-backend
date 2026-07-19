package com.gaokao.essay.backend.model;

import java.time.Instant;

public record PaymentOrder(
    String outTradeNo,
    String orderId,
    String userId,
    String openId,
    String planCode,
    String planName,
    int amountFen,
    String currency,
    String status,
    boolean autoRenew,
    String description,
    String prepayId,
    String transactionId,
    String provider,
    String providerReference,
    String payloadJson,
    Instant paidAt,
    Instant createdAt,
    Instant updatedAt
) {

  public boolean isPaid() {
    return "PAID".equalsIgnoreCase(status);
  }
}
