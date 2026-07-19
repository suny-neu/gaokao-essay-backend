package com.gaokao.essay.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.util.TextUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class WechatService {

  private final GaokaoProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private volatile String cachedAccessToken = "";
  private volatile Instant cachedAccessTokenExpiresAt = Instant.EPOCH;

  public WechatService(GaokaoProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public boolean hasCode2SessionConfig() {
    return !TextUtils.isBlank(properties.getWechat().getAppId())
        && !TextUtils.isBlank(properties.getWechat().getAppSecret());
  }

  public boolean isMsgSecEnabled() {
    return properties.getSecurity().isMsgSecEnabled();
  }

  public String resolveOpenId(String code) {
    if (TextUtils.isBlank(code)) {
      return "";
    }
    if (!hasCode2SessionConfig()) {
      if (properties.isLocalAuthFallbackEnabled()) {
        return "dev_" + TextUtils.sha256(code).substring(0, 24);
      }
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_AUTH_NOT_READY", "后端未配置微信 AppID / AppSecret，暂时无法完成登录");
    }

    String url = properties.getWechat().getCode2SessionUrl()
        + "?appid=" + encode(properties.getWechat().getAppId())
        + "&secret=" + encode(properties.getWechat().getAppSecret())
        + "&js_code=" + encode(code)
        + "&grant_type=authorization_code";
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .GET()
          .timeout(Duration.ofSeconds(12))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode root = objectMapper.readTree(response.body());
      String openId = root.path("openid").asText("");
      if (!TextUtils.isBlank(openId)) {
        return openId;
      }
      int errCode = root.path("errcode").asInt(0);
      if (properties.isLocalAuthFallbackEnabled()) {
        return "dev_" + TextUtils.sha256(code).substring(0, 24);
      }
      throw new ApiException(HttpStatus.UNAUTHORIZED, "WECHAT_LOGIN_FAILED", "微信登录失败：" + root.path("errmsg").asText("errcode=" + errCode));
    } catch (IOException | InterruptedException error) {
      Thread.currentThread().interrupt();
      if (properties.isLocalAuthFallbackEnabled()) {
        return "dev_" + TextUtils.sha256(code).substring(0, 24);
      }
      throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_LOGIN_UNREACHABLE", "微信登录接口暂时不可用，请稍后再试");
    }
  }

  public void checkMessageSecurity(String openId, String text, String label) {
    if (!isMsgSecEnabled()) {
      return;
    }
    String normalized = TextUtils.normalizeForSecurityCheck(text);
    if (TextUtils.isBlank(normalized)) {
      return;
    }
    if (!hasCode2SessionConfig()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MSG_SEC_NOT_READY", "内容安全检查已开启，但微信配置还未补齐");
    }
    for (String chunk : TextUtils.chunkText(normalized, 1000)) {
      doCheckMessageSecurity(openId, chunk, label);
    }
  }

  private void doCheckMessageSecurity(String openId, String text, String label) {
    try {
      String accessToken = getAccessToken();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("openid", TextUtils.isBlank(openId) ? "system" : openId);
      body.put("scene", 2);
      body.put("version", 2);
      body.put("content", text);

      HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getWechat().getMsgSecCheckUrl() + "?access_token=" + encode(accessToken)))
          .timeout(Duration.ofSeconds(12))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode root = objectMapper.readTree(response.body());
      int errCode = root.path("errcode").asInt(-1);
      if (errCode == 0) {
        return;
      }
      String errMessage = root.path("errmsg").asText("msgSecCheck rejected");
      if (List.of(87014, 89401, 20001).contains(errCode)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_NOT_ALLOWED", label + "包含平台不允许的内容，请修改后再试");
      }
      throw new ApiException(HttpStatus.BAD_GATEWAY, "MSG_SEC_FAILED", "内容安全检查失败：" + errMessage);
    } catch (IOException | InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "MSG_SEC_UNREACHABLE", "内容安全检查暂时不可用，请稍后再试");
    }
  }

  private synchronized String getAccessToken() throws IOException, InterruptedException {
    if (!TextUtils.isBlank(cachedAccessToken) && Instant.now().isBefore(cachedAccessTokenExpiresAt.minusSeconds(60))) {
      return cachedAccessToken;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("grant_type", "client_credential");
    body.put("appid", properties.getWechat().getAppId());
    body.put("secret", properties.getWechat().getAppSecret());
    body.put("force_refresh", false);

    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getWechat().getStableTokenUrl()))
        .timeout(Duration.ofSeconds(12))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    JsonNode root = objectMapper.readTree(response.body());
    String accessToken = root.path("access_token").asText("");
    if (TextUtils.isBlank(accessToken)) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_TOKEN_FAILED", "无法获取微信 access_token");
    }
    int expiresIn = Math.max(root.path("expires_in").asInt(7200), 300);
    this.cachedAccessToken = accessToken;
    this.cachedAccessTokenExpiresAt = Instant.now().plusSeconds(expiresIn);
    return cachedAccessToken;
  }

  private String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
