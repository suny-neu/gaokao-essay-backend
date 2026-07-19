package com.gaokao.essay.backend.controller;

import com.gaokao.essay.backend.model.ApiResponse;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.CreateSubscriptionOrderRequest;
import com.gaokao.essay.backend.model.PlanActivateRequest;
import com.gaokao.essay.backend.service.MembershipService;
import com.gaokao.essay.backend.service.WechatPayService;
import com.gaokao.essay.backend.service.SessionService;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

  private final MembershipService membershipService;
  private final SessionService sessionService;
  private final WechatPayService wechatPayService;

  public BillingController(
      MembershipService membershipService,
      SessionService sessionService,
      WechatPayService wechatPayService
  ) {
    this.membershipService = membershipService;
    this.sessionService = sessionService;
    this.wechatPayService = wechatPayService;
  }

  @GetMapping("/plans")
  public ApiResponse<List<Map<String, Object>>> plans() {
    return ApiResponse.ok(membershipService.getPlans());
  }

  @PostMapping("/subscription/debug-activate")
  public ApiResponse<Map<String, Object>> activateDebugSubscription(
      HttpServletRequest servletRequest,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody PlanActivateRequest payload
  ) {
    AuthenticatedUser user = sessionService.requireUser(servletRequest, authorizationHeader, null);
    return ApiResponse.ok(membershipService.activateDebugSubscription(user, payload.getPlanCode(), payload.isAutoRenew()));
  }

  @PostMapping("/subscription/create-order")
  public ApiResponse<Map<String, Object>> createSubscriptionOrder(
      HttpServletRequest servletRequest,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody CreateSubscriptionOrderRequest payload
  ) {
    AuthenticatedUser user = sessionService.requireUser(servletRequest, authorizationHeader, null);
    return ApiResponse.ok(
        wechatPayService.createSubscriptionOrder(user, payload.getPlanCode(), payload.isAutoRenew())
    );
  }

  @GetMapping("/orders/{outTradeNo}")
  public ApiResponse<Map<String, Object>> queryOrderStatus(
      HttpServletRequest servletRequest,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String outTradeNo
  ) {
    AuthenticatedUser user = sessionService.requireUser(servletRequest, authorizationHeader, null);
    Map<String, Object> data = wechatPayService.getOrderStatusForUser(user, outTradeNo, true);
    data.put("entitlement", membershipService.getEntitlement(user));
    return ApiResponse.ok(data);
  }

  @PostMapping("/wechat/notify")
  public ResponseEntity<?> handleWechatPaymentNotify(
      @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
      @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
      @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
      @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
      @RequestBody(required = false) String body
  ) {
    try {
      wechatPayService.handlePaymentNotification(timestamp, nonce, signature, serial, body == null ? "" : body);
      return ResponseEntity.noContent().build();
    } catch (Exception error) {
      return ResponseEntity.badRequest().body(Map.of(
          "code", "FAIL",
          "message", error.getMessage() == null ? "支付回调处理失败" : error.getMessage()
      ));
    }
  }
}
