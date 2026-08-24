package com.gaokao.essay.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.PaymentOrder;
import com.gaokao.essay.backend.repository.PaymentOrderRepository;
import com.gaokao.essay.backend.util.TextUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WechatPayService {

  private static final DateTimeFormatter OUT_TRADE_NO_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Shanghai"));

  private final GaokaoProperties properties;
  private final ObjectMapper objectMapper;
  private final PaymentOrderRepository paymentOrderRepository;
  private final MembershipService membershipService;
  private final HttpClient httpClient;
  private volatile PrivateKey merchantPrivateKey;
  private volatile PublicKey platformPublicKey;

  public WechatPayService(
      GaokaoProperties properties,
      ObjectMapper objectMapper,
      PaymentOrderRepository paymentOrderRepository,
      MembershipService membershipService
  ) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.paymentOrderRepository = paymentOrderRepository;
    this.membershipService = membershipService;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public boolean isEnabled() {
    return properties.getPayment().isEnabled();
  }

  public boolean isReady() {
    return isEnabled() && collectConfigIssues().isEmpty();
  }

  public List<String> collectConfigIssues() {
    List<String> issues = new ArrayList<>();
    if (!isEnabled()) {
      return issues;
    }

    if (TextUtils.isBlank(properties.getWechat().getAppId())) {
      issues.add("WeChat app-id is required for miniapp payment.");
    }
    if (TextUtils.isBlank(properties.getPayment().getNotifyUrl())) {
      issues.add("Payment notify-url is required.");
    }
    if (TextUtils.isBlank(properties.getPayment().getWechat().getMerchantId())) {
      issues.add("WeChat Pay merchant-id is required.");
    }
    if (TextUtils.isBlank(properties.getPayment().getWechat().getMerchantSerialNumber())) {
      issues.add("WeChat Pay merchant-serial-number is required.");
    }
    if (!hasPemMaterialConfigured(
        properties.getPayment().getWechat().getPrivateKeyPem(),
        properties.getPayment().getWechat().getPrivateKeyFile()
    )) {
      issues.add("WeChat Pay merchant private key is missing.");
    }
    if (!hasPemMaterialConfigured(
        properties.getPayment().getWechat().getPlatformPublicKeyPem(),
        properties.getPayment().getWechat().getPlatformPublicKeyFile()
    )) {
      issues.add("WeChat Pay platform public key is missing.");
    }
    String apiV3Key = properties.getPayment().getWechat().getApiV3Key();
    if (TextUtils.isBlank(apiV3Key) || apiV3Key.getBytes(StandardCharsets.UTF_8).length != 32) {
      issues.add("WeChat Pay API v3 key must be exactly 32 bytes.");
    }
    return issues;
  }

  public Map<String, Object> createSubscriptionOrder(AuthenticatedUser user, String planCode, boolean autoRenewRequested) {
    GaokaoProperties.Plan plan = membershipService.requirePlan(planCode);
    membershipService.requirePurchaseAllowed(user, plan);
    ensurePaymentReady();
    Instant now = Instant.now();
    String outTradeNo = buildOutTradeNo();
    String description = buildDescription(plan);

    PaymentOrder draftOrder = new PaymentOrder(
        outTradeNo,
        TextUtils.uid("order"),
        user.userId(),
        user.openId(),
        plan.getCode(),
        plan.getName(),
        Math.max(plan.getPriceFen(), 1),
        properties.getPayment().getCurrency(),
        "CREATED",
        false,
        description,
        "",
        "",
        "wechatpay",
        outTradeNo,
        "",
        null,
        now,
        now
    );
    paymentOrderRepository.save(draftOrder);

    try {
      Map<String, Object> requestBody = new LinkedHashMap<>();
      requestBody.put("appid", properties.getWechat().getAppId());
      requestBody.put("mchid", properties.getPayment().getWechat().getMerchantId());
      requestBody.put("description", description);
      requestBody.put("out_trade_no", outTradeNo);
      requestBody.put("notify_url", properties.getPayment().getNotifyUrl());

      Map<String, Object> amount = new LinkedHashMap<>();
      amount.put("total", draftOrder.amountFen());
      amount.put("currency", draftOrder.currency());
      requestBody.put("amount", amount);

      Map<String, Object> payer = new LinkedHashMap<>();
      payer.put("openid", user.openId());
      requestBody.put("payer", payer);

      String requestJson = objectMapper.writeValueAsString(requestBody);
      URI uri = URI.create(properties.getPayment().getWechat().getJsapiUrl());
      HttpResponse<String> response = sendSignedRequest("POST", uri, requestJson);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw toRemoteApiException("微信支付下单失败", response.statusCode(), response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      String prepayId = root.path("prepay_id").asText("");
      if (TextUtils.isBlank(prepayId)) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_PAY_PREPAY_MISSING", "微信支付下单未返回 prepay_id");
      }

      PaymentOrder prepayOrder = replaceOrder(
          draftOrder,
          "PREPAY_CREATED",
          prepayId,
          draftOrder.transactionId(),
          draftOrder.providerReference(),
          response.body(),
          draftOrder.paidAt(),
          Instant.now()
      );
      paymentOrderRepository.save(prepayOrder);

      String packageValue = "prepay_id=" + prepayId;
      String timeStamp = String.valueOf(Instant.now().getEpochSecond());
      String nonceStr = buildNonce();
      String paySign = signMiniAppPayParams(properties.getWechat().getAppId(), timeStamp, nonceStr, packageValue);

      Map<String, Object> payParams = new LinkedHashMap<>();
      payParams.put("timeStamp", timeStamp);
      payParams.put("nonceStr", nonceStr);
      payParams.put("package", packageValue);
      payParams.put("signType", "RSA");
      payParams.put("paySign", paySign);

      Map<String, Object> data = new LinkedHashMap<>();
      data.put("outTradeNo", prepayOrder.outTradeNo());
      data.put("planCode", prepayOrder.planCode());
      data.put("planName", prepayOrder.planName());
      data.put("amountFen", prepayOrder.amountFen());
      data.put("priceText", String.format("¥%.2f", prepayOrder.amountFen() / 100.0));
      data.put("status", prepayOrder.status());
      data.put("renewalMode", "manual");
      data.put("autoRenewRequested", autoRenewRequested);
      data.put("payParams", payParams);
      data.put("serverTime", Instant.now().toString());
      return data;
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_PAY_PARSE_ERROR", "微信支付下单结果解析失败");
    }
  }

  public Map<String, Object> getOrderStatusForUser(AuthenticatedUser user, String outTradeNo, boolean refreshRemote) {
    PaymentOrder paymentOrder = requireOwnedOrder(user, outTradeNo);
    boolean refreshed = false;
    String syncMessage = "";

    if (refreshRemote && isReady() && !paymentOrder.isPaid()) {
      try {
        paymentOrder = refreshOrderStatusFromWeChat(paymentOrder);
        refreshed = true;
      } catch (ApiException error) {
        syncMessage = error.getMessage();
      }
    }

    Map<String, Object> data = toOrderMap(paymentOrder);
    data.put("refreshAttempted", refreshRemote);
    data.put("statusSource", refreshed ? "wechat-refresh" : "local-cache");
    if (!TextUtils.isBlank(syncMessage)) {
      data.put("syncMessage", syncMessage);
    }
    return data;
  }

  public void handlePaymentNotification(
      String timestamp,
      String nonce,
      String signature,
      String serial,
      String body
  ) {
    ensurePaymentReady();
    if (TextUtils.isBlank(timestamp) || TextUtils.isBlank(nonce) || TextUtils.isBlank(signature) || TextUtils.isBlank(body)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_INVALID", "支付回调缺少必要字段");
    }

    verifyNotificationSignature(timestamp, nonce, body, signature, serial);

    try {
      JsonNode notifyRoot = objectMapper.readTree(body);
      String decryptedText = decryptNotificationResource(notifyRoot.path("resource"));
      JsonNode transaction = objectMapper.readTree(decryptedText);
      String outTradeNo = transaction.path("out_trade_no").asText("");
      String transactionId = transaction.path("transaction_id").asText("");
      String tradeState = transaction.path("trade_state").asText("SUCCESS");

      if (TextUtils.isBlank(outTradeNo)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_ORDER_MISSING", "支付回调缺少商户订单号");
      }
      if (TextUtils.isBlank(transactionId)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_TX_MISSING", "支付回调缺少微信支付订单号");
      }

      PaymentOrder order = paymentOrderRepository.findByOutTradeNo(outTradeNo)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "未找到对应支付订单"));

      String payloadJson = buildNotificationPayload(notifyRoot, transaction);
      if ("SUCCESS".equalsIgnoreCase(tradeState)) {
        applySuccessfulPayment(
            order,
            transactionId,
            payloadJson,
            parseInstantOrNull(transaction.path("success_time").asText(""))
        );
        return;
      }

      paymentOrderRepository.save(replaceOrder(
          order,
          tradeState.toUpperCase(),
          order.prepayId(),
          transactionId,
          order.providerReference(),
          payloadJson,
          order.paidAt(),
          Instant.now()
      ));
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_PARSE_ERROR", "支付回调报文无法解析");
    }
  }

  private PaymentOrder refreshOrderStatusFromWeChat(PaymentOrder order) {
    ensurePaymentReady();
    String merchantId = properties.getPayment().getWechat().getMerchantId();
    String queryPath = "/v3/pay/transactions/out-trade-no/" + order.outTradeNo()
        + "?mchid=" + encodeQuery(merchantId);
    URI uri = URI.create(properties.getPayment().getWechat().getOrderQueryBaseUrl() + queryPath);

    try {
      HttpResponse<String> response = sendSignedRequest("GET", uri, "");
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw toRemoteApiException("微信支付查单失败", response.statusCode(), response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      String tradeState = root.path("trade_state").asText(order.status());
      String transactionId = root.path("transaction_id").asText(order.transactionId());
      Instant successTime = parseInstantOrNull(root.path("success_time").asText(""));
      if ("SUCCESS".equalsIgnoreCase(tradeState) && !TextUtils.isBlank(transactionId)) {
        return applySuccessfulPayment(order, transactionId, response.body(), successTime);
      }

      PaymentOrder updated = replaceOrder(
          order,
          tradeState.toUpperCase(),
          order.prepayId(),
          transactionId,
          order.providerReference(),
          response.body(),
          order.paidAt(),
          Instant.now()
      );
      return paymentOrderRepository.save(updated);
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_PAY_QUERY_PARSE_ERROR", "微信支付查单结果解析失败");
    }
  }

  private PaymentOrder applySuccessfulPayment(
      PaymentOrder order,
      String transactionId,
      String payloadJson,
      Instant paidAt
  ) {
    if (order.isPaid() && TextUtils.lower(order.transactionId()).equals(TextUtils.lower(transactionId))) {
      return order;
    }

    Instant effectivePaidAt = paidAt == null ? Instant.now() : paidAt;
    membershipService.activatePaidSubscription(
        order.userId(),
        order.planCode(),
        "wechatpay",
        transactionId,
        effectivePaidAt
    );

    PaymentOrder updated = replaceOrder(
        order,
        "PAID",
        order.prepayId(),
        transactionId,
        transactionId,
        payloadJson,
        effectivePaidAt,
        Instant.now()
    );
    return paymentOrderRepository.save(updated);
  }

  private PaymentOrder requireOwnedOrder(AuthenticatedUser user, String outTradeNo) {
    PaymentOrder order = paymentOrderRepository.findByOutTradeNo(TextUtils.trimToEmpty(outTradeNo))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "未找到对应支付订单"));
    if (!user.userId().equals(order.userId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_ORDER_FORBIDDEN", "当前账号无权查看此支付订单");
    }
    return order;
  }

  private HttpResponse<String> sendSignedRequest(String method, URI uri, String body) {
    try {
      HttpRequest request = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(15))
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .header("Authorization", buildAuthorizationHeader(method, uri, body))
          .method(method, requestBodyPublisher(method, body))
          .build();
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_PAY_INTERRUPTED", "微信支付请求被中断，请稍后再试");
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_PAY_UNREACHABLE", "微信支付接口暂时不可用，请稍后再试");
    }
  }

  private HttpRequest.BodyPublisher requestBodyPublisher(String method, String body) {
    if ("GET".equalsIgnoreCase(method)) {
      return HttpRequest.BodyPublishers.noBody();
    }
    return HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8);
  }

  private String buildAuthorizationHeader(String method, URI uri, String body) {
    String nonceStr = buildNonce();
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String canonicalUrl = uri.getRawPath()
        + (TextUtils.isBlank(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery());
    String message = method + "\n"
        + canonicalUrl + "\n"
        + timestamp + "\n"
        + nonceStr + "\n"
        + (body == null ? "" : body) + "\n";
    String signature = signWithMerchantKey(message);
    return "WECHATPAY2-SHA256-RSA2048 "
        + "mchid=\"" + properties.getPayment().getWechat().getMerchantId() + "\","
        + "nonce_str=\"" + nonceStr + "\","
        + "signature=\"" + signature + "\","
        + "timestamp=\"" + timestamp + "\","
        + "serial_no=\"" + properties.getPayment().getWechat().getMerchantSerialNumber() + "\"";
  }

  private String signMiniAppPayParams(String appId, String timeStamp, String nonceStr, String packageValue) {
    String message = appId + "\n"
        + timeStamp + "\n"
        + nonceStr + "\n"
        + packageValue + "\n";
    return signWithMerchantKey(message);
  }

  private String signWithMerchantKey(String message) {
    try {
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(loadMerchantPrivateKey());
      signer.update(message.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signer.sign());
    } catch (GeneralSecurityException error) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WECHAT_PAY_SIGN_ERROR", "微信支付签名失败，请检查商户私钥配置");
    }
  }

  private void verifyNotificationSignature(
      String timestamp,
      String nonce,
      String body,
      String signature,
      String serial
  ) {
    String expectedSerial = properties.getPayment().getWechat().getPlatformSerialNumber();
    if (!TextUtils.isBlank(expectedSerial) && !TextUtils.isBlank(serial) && !expectedSerial.equalsIgnoreCase(serial.trim())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_SERIAL_MISMATCH", "支付回调平台证书序列号不匹配");
    }

    String message = timestamp + "\n" + nonce + "\n" + body + "\n";
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(loadPlatformPublicKey());
      verifier.update(message.getBytes(StandardCharsets.UTF_8));
      boolean valid = verifier.verify(Base64.getDecoder().decode(signature));
      if (!valid) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_SIGNATURE_INVALID", "支付回调验签失败");
      }
    } catch (ApiException error) {
      throw error;
    } catch (IllegalArgumentException | GeneralSecurityException error) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_SIGNATURE_INVALID", "支付回调验签失败");
    }
  }

  private String decryptNotificationResource(JsonNode resourceNode) {
    String ciphertext = resourceNode.path("ciphertext").asText("");
    String nonce = resourceNode.path("nonce").asText("");
    String associatedData = resourceNode.path("associated_data").asText("");
    if (TextUtils.isBlank(ciphertext) || TextUtils.isBlank(nonce)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_RESOURCE_INVALID", "支付回调密文不完整");
    }

    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec gcmSpec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
      SecretKeySpec keySpec = new SecretKeySpec(
          properties.getPayment().getWechat().getApiV3Key().getBytes(StandardCharsets.UTF_8),
          "AES"
      );
      cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
      if (!TextUtils.isBlank(associatedData)) {
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
      }
      byte[] plainBytes = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
      return new String(plainBytes, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException error) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_NOTIFY_DECRYPT_FAILED", "支付回调解密失败");
    }
  }

  private String buildNotificationPayload(JsonNode notifyRoot, JsonNode transaction) throws IOException {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("notify", notifyRoot);
    payload.put("transaction", transaction);
    return objectMapper.writeValueAsString(payload);
  }

  private ApiException toRemoteApiException(String summary, int statusCode, String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
      String code = root.path("code").asText("");
      String message = root.path("message").asText("");
      String displayMessage = summary
          + (TextUtils.isBlank(code) ? "" : "：" + code)
          + (TextUtils.isBlank(message) ? "" : " / " + message);
      return new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_PAY_REMOTE_ERROR", displayMessage);
    } catch (IOException ignored) {
      return new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_PAY_REMOTE_ERROR", summary + "：HTTP " + statusCode);
    }
  }

  private Map<String, Object> toOrderMap(PaymentOrder order) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("outTradeNo", order.outTradeNo());
    data.put("planCode", order.planCode());
    data.put("planName", order.planName());
    data.put("amountFen", order.amountFen());
    data.put("priceText", String.format("¥%.2f", order.amountFen() / 100.0));
    data.put("status", order.status());
    data.put("paid", order.isPaid());
    data.put("transactionId", order.transactionId());
    data.put("renewalMode", "manual");
    data.put("createdAt", order.createdAt() == null ? "" : order.createdAt().toString());
    data.put("paidAt", order.paidAt() == null ? "" : order.paidAt().toString());
    return data;
  }

  private PaymentOrder replaceOrder(
      PaymentOrder base,
      String status,
      String prepayId,
      String transactionId,
      String providerReference,
      String payloadJson,
      Instant paidAt,
      Instant updatedAt
  ) {
    return new PaymentOrder(
        base.outTradeNo(),
        base.orderId(),
        base.userId(),
        base.openId(),
        base.planCode(),
        base.planName(),
        base.amountFen(),
        base.currency(),
        status,
        base.autoRenew(),
        base.description(),
        prepayId,
        transactionId,
        base.provider(),
        providerReference,
        payloadJson,
        paidAt,
        base.createdAt(),
        updatedAt
    );
  }

  private PrivateKey loadMerchantPrivateKey() {
    PrivateKey cached = merchantPrivateKey;
    if (cached != null) {
      return cached;
    }
    try {
      String material = resolvePemMaterial(
          properties.getPayment().getWechat().getPrivateKeyPem(),
          properties.getPayment().getWechat().getPrivateKeyFile()
      );
      byte[] decoded = decodePem(material, "PRIVATE KEY");
      PrivateKey parsed = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
      this.merchantPrivateKey = parsed;
      return parsed;
    } catch (GeneralSecurityException error) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WECHAT_PAY_PRIVATE_KEY_INVALID", "微信支付商户私钥格式无效");
    }
  }

  private PublicKey loadPlatformPublicKey() {
    PublicKey cached = platformPublicKey;
    if (cached != null) {
      return cached;
    }
    try {
      String material = resolvePemMaterial(
          properties.getPayment().getWechat().getPlatformPublicKeyPem(),
          properties.getPayment().getWechat().getPlatformPublicKeyFile()
      );
      byte[] decoded = decodePem(material, "PUBLIC KEY");
      PublicKey parsed = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
      this.platformPublicKey = parsed;
      return parsed;
    } catch (GeneralSecurityException error) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WECHAT_PAY_PLATFORM_KEY_INVALID", "微信支付平台公钥格式无效");
    }
  }

  private byte[] decodePem(String pem, String label) {
    if (TextUtils.isBlank(pem)) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WECHAT_PAY_KEY_MISSING", "微信支付密钥配置为空");
    }
    String normalized = pem
        .replace("\\n", "\n")
        .replace("-----BEGIN " + label + "-----", "")
        .replace("-----END " + label + "-----", "")
        .replaceAll("\\s+", "");
    try {
      return Base64.getDecoder().decode(normalized);
    } catch (IllegalArgumentException error) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WECHAT_PAY_KEY_INVALID", "微信支付密钥 Base64 内容无效");
    }
  }

  private String resolvePemMaterial(String inlinePem, String filePath) {
    if (!TextUtils.isBlank(filePath)) {
      try {
        return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
      } catch (IOException error) {
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WECHAT_PAY_KEY_READ_FAILED", "无法读取微信支付密钥文件：" + filePath);
      }
    }
    return inlinePem;
  }

  private boolean hasPemMaterialConfigured(String inlinePem, String filePath) {
    if (!TextUtils.isBlank(inlinePem)) {
      return true;
    }
    if (TextUtils.isBlank(filePath)) {
      return false;
    }
    try {
      return Files.isRegularFile(Path.of(filePath));
    } catch (Exception ignored) {
      return false;
    }
  }

  private void ensurePaymentReady() {
    if (!isEnabled()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "BILLING_DISABLED", "当前环境未开启真实支付");
    }
    List<String> issues = collectConfigIssues();
    if (!issues.isEmpty()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_PAY_NOT_READY", "真实支付配置未完成：" + issues.get(0));
    }
  }

  private String buildOutTradeNo() {
    return "GC" + OUT_TRADE_NO_TIME.format(Instant.now()) + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  private String buildDescription(GaokaoProperties.Plan plan) {
    String prefix = TextUtils.trimToEmpty(properties.getPayment().getOrderDescriptionPrefix());
    if (TextUtils.isBlank(prefix)) {
      prefix = "高考英语作文会员";
    }
    return prefix + "-" + plan.getName();
  }

  private String buildNonce() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private Instant parseInstantOrNull(String value) {
    if (TextUtils.isBlank(value)) {
      return null;
    }
    try {
      return Instant.parse(value.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private String encodeQuery(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
