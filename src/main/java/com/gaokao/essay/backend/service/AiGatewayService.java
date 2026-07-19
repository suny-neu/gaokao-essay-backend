package com.gaokao.essay.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.util.TextUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiGatewayService {

  private static final String TENCENT_OCR_PROVIDER = "tencent-ocr";
  private static final String UMI_OCR_PROVIDER = "umi-ocr";
  private static final String TENCENT_OCR_HOST = "ocr.tencentcloudapi.com";
  private static final String TENCENT_OCR_SERVICE = "ocr";
  private static final String TENCENT_OCR_ALGORITHM = "TC3-HMAC-SHA256";
  private static final DateTimeFormatter TENCENT_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  private final GaokaoProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public AiGatewayService(GaokaoProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
  }

  public boolean isTextGenerationReady() {
    return !TextUtils.isBlank(properties.getAi().getBaseUrl())
        && !TextUtils.isBlank(properties.getAi().getApiKey())
        && !TextUtils.isBlank(properties.getAi().getModel());
  }

  public boolean isVisionReady() {
    if (!properties.getOcr().isEnabled()) {
      return false;
    }
    if (isTencentOcrProvider()) {
      return !TextUtils.isBlank(properties.getOcr().getTencentSecretId())
          && !TextUtils.isBlank(properties.getOcr().getTencentSecretKey())
          && !TextUtils.isBlank(properties.getWechat().getAppId());
    }
    if (isUmiOcrProvider()) {
      return !TextUtils.isBlank(resolveBaseUrl(true));
    }
    String model = TextUtils.isBlank(properties.getOcr().getModel())
        ? properties.getAi().getModel()
        : properties.getOcr().getModel();
    return !TextUtils.isBlank(resolveBaseUrl(true))
        && !TextUtils.isBlank(resolveApiKey(true))
        && !TextUtils.isBlank(model);
  }

  public String getVisionProviderLabel() {
    if (isTencentOcrProvider()) {
      return TENCENT_OCR_PROVIDER;
    }
    if (isUmiOcrProvider()) {
      return UMI_OCR_PROVIDER;
    }
    return "vision-ocr";
  }

  public String requestJsonText(String systemPrompt, String userPrompt) {
    ensureTextReady();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.getAi().getModel());
    body.put("temperature", properties.getAi().getTemperature());
    body.put("messages", List.of(
        message("system", systemPrompt),
        message("user", userPrompt)
    ));
    return invokeOpenAiCompatible(resolveBaseUrl(false), resolveApiKey(false), properties.getAi().getTimeoutSeconds(), body);
  }

  public String requestVisionOcr(String mimeType, String base64Content, String scene) {
    if (!isVisionReady()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OCR_NOT_READY", "OCR 正式识别链路还未接通");
    }
    if (isTencentOcrProvider()) {
      return invokeTencentOcr(base64Content);
    }
    if (isUmiOcrProvider()) {
      return invokeUmiOcr(base64Content);
    }
    String model = TextUtils.isBlank(properties.getOcr().getModel())
        ? properties.getAi().getModel()
        : properties.getOcr().getModel();
    List<Map<String, Object>> content = new ArrayList<>();
    content.add(Map.of(
        "type", "text",
        "text", "请严格执行 OCR 转写，只输出图片里的文字内容。不要总结，不要补充，不要改写。若图片包含英文作文题面，请保持原有换行和编号格式。场景：" + scene
    ));
    content.add(Map.of(
        "type", "image_url",
        "image_url", Map.of("url", "data:" + mimeType + ";base64," + base64Content)
    ));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("temperature", 0);
    body.put("messages", List.of(
        Map.of("role", "user", "content", content)
    ));

    return invokeOpenAiCompatible(resolveBaseUrl(true), resolveApiKey(true), properties.getOcr().getTimeoutSeconds(), body);
  }

  private String invokeOpenAiCompatible(String baseUrl, String apiKey, int timeoutSeconds, Map<String, Object> body) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(joinPath(baseUrl, "/chat/completions")))
          .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 15)))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_FAILED", "大模型上游返回异常：" + response.statusCode());
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode message = root.path("choices").path(0).path("message");
      String content = readMessageContent(message.path("content"));
      if (!TextUtils.isBlank(content)) {
        return content;
      }
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE", "大模型上游没有返回可用内容");
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_UNREACHABLE", "大模型上游暂时不可用，请稍后再试");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_UNREACHABLE", "大模型上游暂时不可用，请稍后再试");
    }
  }

  private String invokeTencentOcr(String base64Content) {
    try {
      String payload = objectMapper.writeValueAsString(buildTencentOcrBody(base64Content));
      Instant now = Instant.now();
      long timestamp = now.getEpochSecond();
      String date = TENCENT_DATE_FORMATTER.format(now);
      String authorization = buildTencentAuthorization(payload, timestamp, date);

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create("https://" + TENCENT_OCR_HOST))
          .timeout(Duration.ofSeconds(Math.max(properties.getOcr().getTimeoutSeconds(), 15)))
          .header("Authorization", authorization)
          .header("Content-Type", "application/json; charset=utf-8")
          .header("Host", TENCENT_OCR_HOST)
          .header("X-TC-Action", properties.getOcr().getTencentAction())
          .header("X-TC-Timestamp", String.valueOf(timestamp))
          .header("X-TC-Version", properties.getOcr().getTencentVersion())
          .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

      if (!TextUtils.isBlank(properties.getOcr().getTencentRegion())) {
        requestBuilder.header("X-TC-Region", properties.getOcr().getTencentRegion());
      }

      HttpResponse<String> response = httpClient.send(requestBuilder.build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_UPSTREAM_FAILED", "腾讯云 OCR 返回异常：" + response.statusCode());
      }
      JsonNode root = objectMapper.readTree(response.body()).path("Response");
      JsonNode error = root.path("Error");
      if (!error.isMissingNode() && error.hasNonNull("Code")) {
        String code = error.path("Code").asText("OCR_UPSTREAM_FAILED");
        String message = error.path("Message").asText("腾讯云 OCR 调用失败");
        throw new ApiException(HttpStatus.BAD_GATEWAY, code, "腾讯云 OCR 调用失败：" + message);
      }
      List<String> lines = new ArrayList<>();
      for (JsonNode item : root.path("TextDetections")) {
        String line = TextUtils.trimToEmpty(item.path("DetectedText").asText(""));
        if (!line.isEmpty()) {
          lines.add(line);
        }
      }
      String text = String.join("\n", lines).trim();
      if (!text.isEmpty()) {
        return text;
      }
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_EMPTY_RESPONSE", "腾讯云 OCR 没有返回可用文字");
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_UPSTREAM_UNREACHABLE", "腾讯云 OCR 暂时不可用，请稍后再试");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_UPSTREAM_UNREACHABLE", "腾讯云 OCR 暂时不可用，请稍后再试");
    }
  }

  private String invokeUmiOcr(String base64Content) {
    try {
      Map<String, Object> options = new LinkedHashMap<>();
      options.put("data.format", "text");

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("base64", base64Content);
      body.put("options", options);

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(joinPath(resolveBaseUrl(true), "/api/ocr")))
          .timeout(Duration.ofSeconds(Math.max(properties.getOcr().getTimeoutSeconds(), 15)))
          .header("Content-Type", "application/json; charset=utf-8");
      if (!TextUtils.isBlank(properties.getOcr().getApiKey())) {
        requestBuilder.header("Authorization", "Bearer " + properties.getOcr().getApiKey());
      }

      HttpResponse<String> response = httpClient.send(
          requestBuilder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8)).build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
      );
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_UPSTREAM_FAILED", "Umi-OCR 返回异常：" + response.statusCode());
      }

      JsonNode root = objectMapper.readTree(response.body());
      int code = root.path("code").asInt(-1);
      if (code == 101) {
        return "";
      }
      if (code != 100) {
        String message = root.path("data").asText("Umi-OCR 调用失败");
        throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_UPSTREAM_FAILED", "Umi-OCR 调用失败：" + message);
      }

      JsonNode data = root.path("data");
      if (data.isTextual()) {
        return data.asText("").trim();
      }
      if (data.isArray()) {
        StringBuilder text = new StringBuilder();
        for (JsonNode item : data) {
          text.append(item.path("text").asText(""));
          text.append(item.path("end").asText("\n"));
        }
        return text.toString().trim();
      }
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_EMPTY_RESPONSE", "Umi-OCR 没有返回可用文字");
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_UPSTREAM_UNREACHABLE", "Umi-OCR 暂时不可用，请稍后再试");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "OCR_UPSTREAM_UNREACHABLE", "Umi-OCR 暂时不可用，请稍后再试");
    }
  }

  private Map<String, Object> buildTencentOcrBody(String base64Content) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ImageBase64", base64Content);
    body.put("LanguageType", TextUtils.isBlank(properties.getOcr().getTencentLanguageType())
        ? "zh"
        : properties.getOcr().getTencentLanguageType());
    return body;
  }

  private String buildTencentAuthorization(String payload, long timestamp, String date) {
    String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
        + "host:" + TENCENT_OCR_HOST + "\n"
        + "x-tc-action:" + properties.getOcr().getTencentAction().toLowerCase(Locale.ROOT) + "\n";
    String signedHeaders = "content-type;host;x-tc-action";
    String canonicalRequest = "POST\n"
        + "/\n"
        + "\n"
        + canonicalHeaders
        + "\n"
        + signedHeaders + "\n"
        + sha256Hex(payload);
    String credentialScope = date + "/" + TENCENT_OCR_SERVICE + "/tc3_request";
    String stringToSign = TENCENT_OCR_ALGORITHM + "\n"
        + timestamp + "\n"
        + credentialScope + "\n"
        + sha256Hex(canonicalRequest);
    byte[] secretDate = hmacSha256(("TC3" + properties.getOcr().getTencentSecretKey()).getBytes(StandardCharsets.UTF_8), date);
    byte[] secretService = hmacSha256(secretDate, TENCENT_OCR_SERVICE);
    byte[] secretSigning = hmacSha256(secretService, "tc3_request");
    String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));
    return TENCENT_OCR_ALGORITHM
        + " Credential=" + properties.getOcr().getTencentSecretId() + "/" + credentialScope
        + ", SignedHeaders=" + signedHeaders
        + ", Signature=" + signature;
  }

  private byte[] hmacSha256(byte[] key, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception error) {
      throw new IllegalStateException("Unable to sign Tencent OCR request", error);
    }
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  private Map<String, Object> message(String role, String content) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", role);
    message.put("content", content);
    return message;
  }

  private String readMessageContent(JsonNode contentNode) {
    if (contentNode.isTextual()) {
      return contentNode.asText("");
    }
    if (contentNode.isArray()) {
      StringBuilder builder = new StringBuilder();
      for (JsonNode item : contentNode) {
        if (item.path("type").asText("").equals("text")) {
          builder.append(item.path("text").asText(""));
        }
      }
      return builder.toString();
    }
    return "";
  }

  private void ensureTextReady() {
    if (!isTextGenerationReady()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_READY", "正式生成链路未配置完成，请先补齐大模型地址、密钥和模型名");
    }
  }

  private String joinPath(String baseUrl, String suffix) {
    if (baseUrl.endsWith(suffix)) {
      return baseUrl;
    }
    if (baseUrl.endsWith("/")) {
      return baseUrl.substring(0, baseUrl.length() - 1) + suffix;
    }
    if (suffix.startsWith("/api/")) {
      return baseUrl + suffix;
    }
    if (baseUrl.endsWith("/v1")) {
      return baseUrl + suffix;
    }
    return baseUrl + "/v1" + suffix;
  }

  private String resolveBaseUrl(boolean ocr) {
    return ocr ? properties.getOcr().getBaseUrl() : properties.getAi().getBaseUrl();
  }

  private String resolveApiKey(boolean ocr) {
    return ocr ? properties.getOcr().getApiKey() : properties.getAi().getApiKey();
  }

  private boolean isTencentOcrProvider() {
    return TENCENT_OCR_PROVIDER.equalsIgnoreCase(TextUtils.trimToEmpty(properties.getOcr().getProviderName()));
  }

  private boolean isUmiOcrProvider() {
    return UMI_OCR_PROVIDER.equalsIgnoreCase(TextUtils.trimToEmpty(properties.getOcr().getProviderName()));
  }
}
