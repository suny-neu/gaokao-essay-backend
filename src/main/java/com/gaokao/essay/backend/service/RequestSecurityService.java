package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.security.AbuseProtectionStore;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Duration;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RequestSecurityService {

  private final GaokaoProperties properties;
  private final AbuseProtectionStore store;

  public RequestSecurityService(GaokaoProperties properties, AbuseProtectionStore store) {
    this.properties = properties;
    this.store = store;
  }

  public void checkAuthAttempt(HttpServletRequest request) {
    checkByIp(request, "auth", properties.getSecurity().getAuthPerMinute(), 60, "登录请求过于频繁，请稍后再试");
  }

  public void checkEssaySubmission(HttpServletRequest request, String userId) {
    checkEssaySubmission(request, userId, "");
  }

  public void checkEssaySubmission(HttpServletRequest request, String userId, String deviceId) {
    requireDeviceId(deviceId);
    checkByIp(request, "essay-ip", properties.getSecurity().getEssaySubmitPerMinute(), 60, "提交过于频繁，请稍后再试");
    checkByUser(userId, "essay-user", properties.getSecurity().getEssaySubmitPerMinute(), 60, "你的提交频率过高，请稍后再试");
    check("essay-device", deviceId, properties.getSecurity().getEssaySubmitPerMinute(), 60, "当前设备提交过于频繁，请稍后再试");
  }

  public void checkHistoryRead(HttpServletRequest request, String userId) {
    checkByIp(request, "history-ip", properties.getSecurity().getHistoryReadPerMinute(), 60, "查询过于频繁，请稍后再试");
    checkByUser(userId, "history-user", properties.getSecurity().getHistoryReadPerMinute(), 60, "你的查询频率过高，请稍后再试");
  }

  public void checkOcr(HttpServletRequest request, String userId) {
    checkOcr(request, userId, "");
  }

  public void checkOcr(HttpServletRequest request, String userId, String deviceId) {
    requireDeviceId(deviceId);
    checkByIp(request, "ocr-ip", properties.getSecurity().getOcrPerMinute(), 60, "OCR 请求过于频繁，请稍后再试");
    checkByUser(userId, "ocr-user", properties.getSecurity().getOcrPerMinute(), 60, "你的 OCR 请求过于频繁，请稍后再试");
    check("ocr-device", deviceId, properties.getSecurity().getOcrPerMinute(), 60, "当前设备 OCR 请求过于频繁，请稍后再试");
  }

  public void checkChallengeAttempt(HttpServletRequest request) {
    checkByIp(request, "challenge-ip", properties.getSecurity().getChallengePerMinute(), 60, "验证请求过于频繁，请稍后再试");
  }

  private void checkByIp(HttpServletRequest request, String scope, int limit, int windowSeconds, String message) {
    check(scope, resolveClientIp(request), limit, windowSeconds, message);
  }

  private void checkByUser(String userId, String scope, int limit, int windowSeconds, String message) {
    check(scope, (userId == null || userId.isBlank()) ? "anonymous" : userId, limit, windowSeconds, message);
  }

  private void check(String scope, String subject, int limit, int windowSeconds, String message) {
    if (!properties.getSecurity().isRateLimitEnabled() || limit <= 0 || windowSeconds <= 0) {
      return;
    }

    String key = "rate:" + scope + ":" + TextUtils.sha256(subject).substring(0, 32);
    if (!store.tryConsume(key, limit, Duration.ofSeconds(windowSeconds))) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }
  }

  public String requireDeviceId(String deviceId) {
    String normalized = TextUtils.trimToEmpty(deviceId);
    if (!normalized.matches("[A-Za-z0-9_-]{16,128}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_ID_REQUIRED", "设备标识缺失或不合法，请重新进入小程序");
    }
    return normalized;
  }

  private String resolveClientIp(HttpServletRequest request) {
    return resolveClientIpStatic(request);
  }

  public static String resolveClientIpStatic(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }

    String forwardedFor = normalizeHeader(request.getHeader("X-Forwarded-For"));
    if (!forwardedFor.isBlank()) {
      int index = forwardedFor.indexOf(',');
      return index >= 0 ? forwardedFor.substring(0, index).trim() : forwardedFor;
    }

    String realIp = normalizeHeader(request.getHeader("X-Real-IP"));
    if (!realIp.isBlank()) {
      return realIp;
    }

    String remoteAddr = request.getRemoteAddr();
    return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr.trim();
  }

  private static String normalizeHeader(String value) {
    return value == null ? "" : value.trim();
  }

}
