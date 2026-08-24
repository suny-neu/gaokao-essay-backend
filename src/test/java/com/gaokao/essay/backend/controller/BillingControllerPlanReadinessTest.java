package com.gaokao.essay.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.service.MembershipService;
import com.gaokao.essay.backend.service.SessionService;
import com.gaokao.essay.backend.service.WechatPayService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BillingControllerPlanReadinessTest {

  @Test
  void shouldMarkPlansUnavailableWhenPaymentIsDisabled() {
    WechatPayService payment = Mockito.mock(WechatPayService.class);
    when(payment.isEnabled()).thenReturn(false);
    when(payment.isReady()).thenReturn(false);

    List<Map<String, Object>> plans = controller(payment).plans().getData();

    assertEquals("disabled", plans.get(0).get("paymentMode"));
    assertEquals(false, plans.get(0).get("purchasable"));
  }

  @Test
  void shouldMarkPlansUnavailableWhenPaymentConfigurationIsIncomplete() {
    WechatPayService payment = Mockito.mock(WechatPayService.class);
    when(payment.isEnabled()).thenReturn(true);
    when(payment.isReady()).thenReturn(false);

    List<Map<String, Object>> plans = controller(payment).plans().getData();

    assertEquals("configured-but-unready", plans.get(0).get("paymentMode"));
    assertEquals(false, plans.get(0).get("purchasable"));
  }

  private BillingController controller(WechatPayService payment) {
    MembershipService membership = new MembershipService(
        new GaokaoProperties(),
        Mockito.mock(com.gaokao.essay.backend.repository.UserUsageQuotaRepository.class),
        Mockito.mock(com.gaokao.essay.backend.repository.UserSubscriptionRepository.class),
        Mockito.mock(com.gaokao.essay.backend.security.AbuseProtectionStore.class)
    );
    return new BillingController(membership, Mockito.mock(SessionService.class), payment);
  }
}
