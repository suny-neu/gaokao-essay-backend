package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.security.AbuseProtectionStore;
import com.gaokao.essay.backend.util.TextUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 一次性 Challenge Token 服务。
 *
 * <p>用于防止 AI Token 被恶意刷量：每次消耗 AI 资源的请求（作文批改 / OCR）
 * 必须先通过 {@code GET /api/gaokao-essay/challenge} 获取一个一次性令牌，
 * 然后在后续请求中通过 {@code X-Challenge} Header 携带该令牌。
 * 服务端验证后立即销毁，确保每个令牌只能使用一次。</p>
 */
@Service
public class ChallengeService {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final GaokaoProperties properties;
  private final AbuseProtectionStore store;

  public ChallengeService(GaokaoProperties properties, AbuseProtectionStore store) {
    this.properties = properties;
    this.store = store;
  }

  public long getChallengeTtlSeconds() {
    return properties.getSecurity().getChallengeTtlSeconds();
  }

  /**
   * 为指定用户签发一个一次性 Challenge Token。
   *
   * @param userId 用户 ID（或 IP 地址，用于匿名场景）
   * @return Challenge Token 字符串，客户端需在后续请求中通过 X-Challenge Header 携带
   */
  public String issueChallenge(String userId) {
    if (!properties.getSecurity().isChallengeEnabled()) {
      return "";
    }

    String subject = TextUtils.isBlank(userId) ? "anonymous" : userId;
    long ttl = properties.getSecurity().getChallengeTtlSeconds();

    // 生成随机 nonce（16 字节）
    byte[] nonceBytes = new byte[16];
    SECURE_RANDOM.nextBytes(nonceBytes);
    String nonce = URL_ENCODER.encodeToString(nonceBytes);

    // 生成签名：HMAC-SHA256(authTokenSecret, subject + ":" + nonce + ":" + expiresAt)
    String token = nonce + "." + URL_ENCODER.encodeToString(new byte[8]);
    store.putChallenge(hash(token), hash(subject), Duration.ofSeconds(Math.max(ttl, 1)));
    return token;
  }

  /**
   * 验证并消费一个 Challenge Token（一次性使用）。
   *
   * @param token         客户端通过 X-Challenge Header 携带的令牌
   * @param expectedUserId 期望的用户 ID（或 IP 地址）
   * @throws ApiException 如果令牌无效、已过期、已使用或用户不匹配
   */
  public void consumeChallenge(String token, String expectedUserId) {
    if (!properties.getSecurity().isChallengeEnabled()) {
      return;
    }

    if (TextUtils.isBlank(token)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "CHALLENGE_REQUIRED",
          "缺少验证令牌，请刷新后重试"
      );
    }

    String expectedSubject = TextUtils.isBlank(expectedUserId) ? "anonymous" : expectedUserId;

    if (!store.consumeChallenge(hash(token), hash(expectedSubject))) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "CHALLENGE_ALREADY_USED",
          "验证令牌已过期、已使用或与当前用户不匹配，请重新获取"
      );
    }
  }

  private String hash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        hexString.append(String.format("%02x", b));
      }
      return hexString.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 计算失败", e);
    }
  }

}
