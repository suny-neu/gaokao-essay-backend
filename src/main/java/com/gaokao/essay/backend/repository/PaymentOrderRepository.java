package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.PaymentOrder;
import java.util.Optional;

public interface PaymentOrderRepository {

  Optional<PaymentOrder> findByOutTradeNo(String outTradeNo);

  PaymentOrder save(PaymentOrder paymentOrder);
}
