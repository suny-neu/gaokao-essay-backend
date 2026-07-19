package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.PaymentOrder;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.store.StateStore;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StateStorePaymentOrderRepository implements PaymentOrderRepository {

  private final StateStore stateStore;

  public StateStorePaymentOrderRepository(StateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public Optional<PaymentOrder> findByOutTradeNo(String outTradeNo) {
    return stateStore.read(state -> {
      AppState.PaymentOrderState snapshot = state.paymentOrders.get(outTradeNo);
      if (snapshot == null) {
        return Optional.empty();
      }
      return Optional.of(new PaymentOrder(
          snapshot.outTradeNo,
          snapshot.orderId,
          snapshot.userId,
          snapshot.openId,
          snapshot.planCode,
          snapshot.planName,
          snapshot.amountFen,
          snapshot.currency,
          snapshot.status,
          snapshot.autoRenew,
          snapshot.description,
          snapshot.prepayId,
          snapshot.transactionId,
          snapshot.provider,
          snapshot.providerReference,
          snapshot.payloadJson,
          parseNullableInstant(snapshot.paidAt),
          parseInstant(snapshot.createdAt),
          parseInstant(snapshot.updatedAt)
      ));
    });
  }

  @Override
  public PaymentOrder save(PaymentOrder paymentOrder) {
    stateStore.write(state -> {
      AppState.PaymentOrderState snapshot = new AppState.PaymentOrderState();
      snapshot.outTradeNo = paymentOrder.outTradeNo();
      snapshot.orderId = paymentOrder.orderId();
      snapshot.userId = paymentOrder.userId();
      snapshot.openId = paymentOrder.openId();
      snapshot.planCode = paymentOrder.planCode();
      snapshot.planName = paymentOrder.planName();
      snapshot.amountFen = paymentOrder.amountFen();
      snapshot.currency = paymentOrder.currency();
      snapshot.status = paymentOrder.status();
      snapshot.autoRenew = paymentOrder.autoRenew();
      snapshot.description = paymentOrder.description();
      snapshot.prepayId = paymentOrder.prepayId();
      snapshot.transactionId = paymentOrder.transactionId();
      snapshot.provider = paymentOrder.provider();
      snapshot.providerReference = paymentOrder.providerReference();
      snapshot.payloadJson = paymentOrder.payloadJson();
      snapshot.paidAt = formatNullable(paymentOrder.paidAt());
      snapshot.createdAt = formatNullable(paymentOrder.createdAt());
      snapshot.updatedAt = formatNullable(paymentOrder.updatedAt());
      state.paymentOrders.put(paymentOrder.outTradeNo(), snapshot);
      return null;
    });
    return paymentOrder;
  }

  private Instant parseInstant(String value) {
    return TextUtils.isBlank(value) ? Instant.now() : Instant.parse(value);
  }

  private Instant parseNullableInstant(String value) {
    return TextUtils.isBlank(value) ? null : Instant.parse(value);
  }

  private String formatNullable(Instant instant) {
    return instant == null ? "" : TextUtils.formatInstant(instant);
  }
}
