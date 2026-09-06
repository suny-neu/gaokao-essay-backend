package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.PaymentOrder;
import java.util.Optional;

public interface PaymentOrderRepository {

  Optional<PaymentOrder> findByOutTradeNo(String outTradeNo);

  /**
   * 查找用户同一套餐下最近一笔仍未完成的订单（CREATED / PREPAY_CREATED），用于避免重复下单。
   */
  Optional<PaymentOrder> findLatestPendingByUserId(String userId, String planCode);

  PaymentOrder save(PaymentOrder paymentOrder);
}
