package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.PaymentOrder;
import com.gaokao.essay.backend.model.UserSubscription;
import com.gaokao.essay.backend.repository.PaymentOrderRepository;
import com.gaokao.essay.backend.repository.UserSubscriptionRepository;
import com.gaokao.essay.backend.repository.UserUsageQuotaRepository;
import com.gaokao.essay.backend.security.InMemoryAbuseProtectionStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WechatPayServiceLifetimePurchaseTest {

  @Test
  void shouldRejectAnotherFounderLifetimeOrderBeforeCreatingAPayableOrderForActiveFounderLifetime() {
    Instant now = Instant.parse("2026-07-10T10:00:00Z");
    GaokaoProperties properties = readyPaymentProperties();
    InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
    subscriptions.save(new UserSubscription(
        "user_1", "founder_lifetime", "创始终身会员", "ACTIVE", now, null,
        false, "wechatpay", "lifetime_tx", now
    ));
    InMemoryPaymentOrderRepository orders = new InMemoryPaymentOrderRepository();
    MembershipService membership = new MembershipService(
        properties,
        Mockito.mock(UserUsageQuotaRepository.class),
        subscriptions,
        new InMemoryAbuseProtectionStore()
    );
    WechatPayService service = new WechatPayService(properties, new ObjectMapper(), orders, membership);

    ApiException error = assertThrows(ApiException.class, () -> service.createSubscriptionOrder(
        new AuthenticatedUser("user_1", "open_1", now, now.plusSeconds(3600)), "founder_lifetime", false
    ));

    assertEquals("FOUNDER_LIFETIME_ALREADY_ACTIVE", error.getCode());
    assertTrue(orders.store.isEmpty());
  }

  private GaokaoProperties readyPaymentProperties() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getWechat().setAppId("wx-test");
    properties.getPayment().setEnabled(true);
    properties.getPayment().setNotifyUrl("https://example.test/payment/notify");
    properties.getPayment().getWechat().setMerchantId("merchant-test");
    properties.getPayment().getWechat().setMerchantSerialNumber("serial-test");
    properties.getPayment().getWechat().setPrivateKeyPem("configured-private-key");
    properties.getPayment().getWechat().setPlatformPublicKeyPem("configured-platform-key");
    properties.getPayment().getWechat().setApiV3Key("12345678901234567890123456789012");
    properties.getPayment().getWechat().setJsapiUrl("http://127.0.0.1:1/v3/pay/transactions/jsapi");
    return properties;
  }

  private static final class InMemorySubscriptionRepository implements UserSubscriptionRepository {
    private final Map<String, UserSubscription> store = new HashMap<>();

    @Override
    public Optional<UserSubscription> findByUserId(String userId) {
      return Optional.ofNullable(store.get(userId));
    }

    @Override
    public UserSubscription save(UserSubscription subscription) {
      store.put(subscription.userId(), subscription);
      return subscription;
    }
  }

  private static final class InMemoryPaymentOrderRepository implements PaymentOrderRepository {
    private final Map<String, PaymentOrder> store = new HashMap<>();

    @Override
    public Optional<PaymentOrder> findByOutTradeNo(String outTradeNo) {
      return Optional.ofNullable(store.get(outTradeNo));
    }

    @Override
    public PaymentOrder save(PaymentOrder paymentOrder) {
      store.put(paymentOrder.outTradeNo(), paymentOrder);
      return paymentOrder;
    }
  }
}
