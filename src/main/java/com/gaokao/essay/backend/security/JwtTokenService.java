package com.gaokao.essay.backend.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private final GaokaoProperties properties;
  private final ObjectMapper objectMapper;

  public JwtTokenService(GaokaoProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public String issueToken(String userId, String openId) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(Math.max(properties.getAuthTokenExpireSeconds(), 300L));

    Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sub", userId);
    payload.put("openId", openId);
    payload.put("iat", now.getEpochSecond());
    payload.put("exp", expiresAt.getEpochSecond());

    String encodedHeader = base64UrlJson(header);
    String encodedPayload = base64UrlJson(payload);
    String signingInput = encodedHeader + "." + encodedPayload;
    return signingInput + "." + sign(signingInput);
  }

  public AuthenticatedUser parse(String token) {
    if (token == null || token.isBlank()) {
      throw unauthorized("缺少有效登录态");
    }
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw unauthorized("登录态格式不合法");
    }

    String signingInput = parts[0] + "." + parts[1];
    String expectedSignature = sign(signingInput);
    if (!expectedSignature.equals(parts[2])) {
      throw unauthorized("登录态签名校验失败");
    }

    Map<String, Object> claims;
    try {
      claims = objectMapper.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {
      });
    } catch (Exception exception) {
      throw unauthorized("登录态载荷无法解析");
    }

    Instant issuedAt = Instant.ofEpochSecond(longValue(claims.get("iat")));
    Instant expiresAt = Instant.ofEpochSecond(longValue(claims.get("exp")));
    if (!expiresAt.isAfter(Instant.now())) {
      throw unauthorized("登录态已过期");
    }

    return new AuthenticatedUser(
        String.valueOf(claims.get("sub")),
        String.valueOf(claims.get("openId")),
        issuedAt,
        expiresAt
    );
  }

  public long expiresInSeconds() {
    return Math.max(properties.getAuthTokenExpireSeconds(), 300L);
  }

  private String base64UrlJson(Map<String, Object> data) {
    try {
      return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(data));
    } catch (Exception exception) {
      throw new IllegalStateException("无法序列化 JWT 数据", exception);
    }
  }

  private String sign(String signingInput) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.getAuthTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("JWT 签名失败", exception);
    }
  }

  private long longValue(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }

  private ApiException unauthorized(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
  }
}
