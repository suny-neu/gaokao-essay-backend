package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.PaymentOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPaymentOrderRepository implements PaymentOrderRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcPaymentOrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<PaymentOrder> findByOutTradeNo(String outTradeNo) {
    List<PaymentOrder> results = jdbcTemplate.query(
        """
        SELECT out_trade_no, order_id, user_id, open_id, plan_code, plan_name,
               amount_fen, currency, status, auto_renew, description, prepay_id,
               transaction_id, provider, provider_reference, payload_json,
               paid_at, created_at, updated_at
        FROM payment_order
        WHERE out_trade_no = ?
        LIMIT 1
        """,
        (resultSet, rowNum) -> mapRow(resultSet),
        outTradeNo
    );
    return results.stream().findFirst();
  }

  @Override
  public PaymentOrder save(PaymentOrder paymentOrder) {
    int updatedRows = jdbcTemplate.update(
        """
        UPDATE payment_order
        SET order_id = ?, user_id = ?, open_id = ?, plan_code = ?, plan_name = ?,
            amount_fen = ?, currency = ?, status = ?, auto_renew = ?, description = ?,
            prepay_id = ?, transaction_id = ?, provider = ?, provider_reference = ?,
            payload_json = ?, paid_at = ?, created_at = ?, updated_at = ?
        WHERE out_trade_no = ?
        """,
        paymentOrder.orderId(),
        paymentOrder.userId(),
        paymentOrder.openId(),
        paymentOrder.planCode(),
        paymentOrder.planName(),
        paymentOrder.amountFen(),
        paymentOrder.currency(),
        paymentOrder.status(),
        paymentOrder.autoRenew(),
        paymentOrder.description(),
        paymentOrder.prepayId(),
        paymentOrder.transactionId(),
        paymentOrder.provider(),
        paymentOrder.providerReference(),
        paymentOrder.payloadJson(),
        toTimestamp(paymentOrder.paidAt()),
        toTimestamp(paymentOrder.createdAt()),
        toTimestamp(paymentOrder.updatedAt()),
        paymentOrder.outTradeNo()
    );

    if (updatedRows == 0) {
      jdbcTemplate.update(
          """
          INSERT INTO payment_order (
              out_trade_no, order_id, user_id, open_id, plan_code, plan_name,
              amount_fen, currency, status, auto_renew, description, prepay_id,
              transaction_id, provider, provider_reference, payload_json, paid_at,
              created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          paymentOrder.outTradeNo(),
          paymentOrder.orderId(),
          paymentOrder.userId(),
          paymentOrder.openId(),
          paymentOrder.planCode(),
          paymentOrder.planName(),
          paymentOrder.amountFen(),
          paymentOrder.currency(),
          paymentOrder.status(),
          paymentOrder.autoRenew(),
          paymentOrder.description(),
          paymentOrder.prepayId(),
          paymentOrder.transactionId(),
          paymentOrder.provider(),
          paymentOrder.providerReference(),
          paymentOrder.payloadJson(),
          toTimestamp(paymentOrder.paidAt()),
          toTimestamp(paymentOrder.createdAt()),
          toTimestamp(paymentOrder.updatedAt())
      );
    }

    return paymentOrder;
  }

  private PaymentOrder mapRow(ResultSet resultSet) throws SQLException {
    return new PaymentOrder(
        resultSet.getString("out_trade_no"),
        resultSet.getString("order_id"),
        resultSet.getString("user_id"),
        resultSet.getString("open_id"),
        resultSet.getString("plan_code"),
        resultSet.getString("plan_name"),
        resultSet.getInt("amount_fen"),
        resultSet.getString("currency"),
        resultSet.getString("status"),
        resultSet.getBoolean("auto_renew"),
        resultSet.getString("description"),
        resultSet.getString("prepay_id"),
        resultSet.getString("transaction_id"),
        resultSet.getString("provider"),
        resultSet.getString("provider_reference"),
        resultSet.getString("payload_json"),
        toInstant(resultSet.getTimestamp("paid_at")),
        toInstant(resultSet.getTimestamp("created_at")),
        toInstant(resultSet.getTimestamp("updated_at"))
    );
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
