package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.util.TextUtils;
import com.gaokao.essay.backend.security.AbuseProtectionStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class StartupAuditService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(StartupAuditService.class);

  private final GaokaoProperties properties;
  private final AiGatewayService aiGatewayService;
  private final OcrService ocrService;
  private final WechatService wechatService;
  private final WechatPayService wechatPayService;
  private final AbuseProtectionStore abuseProtectionStore;
  private volatile List<String> lastIssues = List.of();

  public StartupAuditService(
      GaokaoProperties properties,
      AiGatewayService aiGatewayService,
      OcrService ocrService,
      WechatService wechatService,
      WechatPayService wechatPayService,
      AbuseProtectionStore abuseProtectionStore
  ) {
    this.properties = properties;
    this.aiGatewayService = aiGatewayService;
    this.ocrService = ocrService;
    this.wechatService = wechatService;
    this.wechatPayService = wechatPayService;
    this.abuseProtectionStore = abuseProtectionStore;
  }

  @Override
  public void run(ApplicationArguments args) {
    List<String> issues = collectIssues();
    lastIssues = List.copyOf(issues);
    if (issues.isEmpty()) {
      log.info("Startup audit passed: runtime configuration looks review-ready.");
      return;
    }

    issues.forEach(issue -> log.warn("Startup audit: {}", issue));
    if (properties.getRuntime().isStrictStartupChecks()) {
      throw new IllegalStateException("Startup audit failed with " + issues.size() + " issue(s). Check logs for details.");
    }
  }

  public boolean isReviewReady() {
    return lastIssues.isEmpty();
  }

  public List<String> getLastIssues() {
    return Collections.unmodifiableList(lastIssues);
  }

  public Map<String, Object> getCapabilities() {
    Map<String, Object> capabilities = new LinkedHashMap<>();
    capabilities.put("generationAvailable", aiGatewayService.isTextGenerationReady());
    capabilities.put("generationMode", aiGatewayService.isTextGenerationReady() ? "live" : "disabled");
    capabilities.put("ocrEnabled", ocrService.isEnabled());
    capabilities.put("ocrMode", ocrService.isEnabled() ? (ocrService.isReady() ? aiGatewayService.getVisionProviderLabel() : "configured-but-unready") : "disabled");
    capabilities.put("msgSecEnabled", properties.getSecurity().isMsgSecEnabled());
    capabilities.put("knowledgeEnabled", properties.getKnowledge().isEnabled());
    capabilities.put("storageMode", properties.getStorage().getDatabase().isEnabled() ? properties.getStorage().getDatabase().resolveKind() : "state-file");
    capabilities.put("authMode", "jwt");
    capabilities.put("debugSubscriptionEnabled", properties.getMembership().isAllowDebugSubscriptionActivate());
    capabilities.put("persistentAbuseProtection", abuseProtectionStore.isPersistent());
    capabilities.put("paymentEnabled", properties.getPayment().isEnabled());
    capabilities.put("paymentMode", properties.getPayment().isEnabled()
        ? (wechatPayService.isReady() ? "live" : "configured-but-unready")
        : "disabled");
    return capabilities;
  }

  private List<String> collectIssues() {
    List<String> issues = new ArrayList<>();

    if ("change-me-local-secret".equals(properties.getAuthTokenSecret())) {
      issues.add("JWT secret is still the default placeholder.");
    }
    if (properties.isLocalAuthFallbackEnabled()) {
      issues.add("Local auth fallback is still enabled.");
    }
    if (properties.isRequestOpenIdFallbackEnabled()) {
      issues.add("Protected APIs still allow openId fallback.");
    }
    if (!properties.getSecurity().isMsgSecEnabled()) {
      issues.add("WeChat msgSecCheck enforcement is still disabled.");
    }
    if (!properties.getSecurity().isRateLimitEnabled()) {
      issues.add("Sensitive APIs are still missing request rate limiting.");
    }
    if (!properties.getSecurity().isChallengeEnabled()) {
      issues.add("One-time challenge protection is disabled.");
    }
    if (!properties.getWechat().isStrictCode2Session()) {
      issues.add("Strict WeChat code2session verification is disabled.");
    }
    if (properties.getSecurity().isRedisRequired() && !abuseProtectionStore.isPersistent()) {
      issues.add("Release runtime requires persistent Redis abuse protection.");
    }
    if (properties.getMembership().getTrialDailyLimit() <= 0
        || properties.getMembership().getDeviceDailyLimit() <= 0
        || properties.getMembership().getIpDailyLimit() <= 0) {
      issues.add("Daily account, device and IP abuse limits must all be greater than zero.");
    }
    if ((properties.getSecurity().isMsgSecEnabled() || properties.getWechat().isStrictCode2Session()) && !wechatService.hasCode2SessionConfig()) {
      issues.add("WeChat app-id / app-secret has not been configured.");
    }
    if (!aiGatewayService.isTextGenerationReady()) {
      issues.add("No real essay generation provider is configured.");
    }
    if (ocrService.isEnabled() && !ocrService.isReady()) {
      issues.add("OCR is enabled, but OCR upstream configuration is incomplete.");
    }
    if (properties.getMembership().isAllowDebugSubscriptionActivate()) {
      issues.add("Membership debug subscription activation is still enabled.");
    }
    if (properties.getPayment().isEnabled()) {
      issues.addAll(wechatPayService.collectConfigIssues());
    }
    if (properties.getStorage().getDatabase().isEnabled()) {
      if (TextUtils.isBlank(properties.getStorage().getDatabase().getUrl())) {
        issues.add("Database storage is enabled, but GAOKAO_DATABASE_URL is blank.");
      }
      if (TextUtils.isBlank(properties.getStorage().getDatabase().getUsername())) {
        issues.add("Database storage is enabled, but GAOKAO_DATABASE_USERNAME is blank.");
      }
    } else if (properties.getRuntime().isStrictStartupChecks()) {
      issues.add("Release-like runtime must use database storage instead of local state-file storage.");
    }
    if (!properties.getKnowledge().isEnabled()) {
      issues.add("Structured knowledge-base hook is disabled.");
    }
    return issues;
  }
}
