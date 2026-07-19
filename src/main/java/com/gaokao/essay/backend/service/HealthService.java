package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

  private final GaokaoProperties properties;
  private final StartupAuditService startupAuditService;

  public HealthService(
      GaokaoProperties properties,
      StartupAuditService startupAuditService
  ) {
    this.properties = properties;
    this.startupAuditService = startupAuditService;
  }

  public Map<String, Object> buildHealth() {
    List<String> issues = startupAuditService.getLastIssues();
    boolean databaseEnabled = properties.getStorage().getDatabase().isEnabled();
    String databaseKind = databaseEnabled
        ? properties.getStorage().getDatabase().resolveKind()
        : "state-file";

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", issues.isEmpty() ? "up" : "degraded");
    data.put("reviewReady", startupAuditService.isReviewReady());
    data.put("issuesCount", issues.size());
    data.put("issues", sanitizeIssues(issues));
    data.put("source", "remote");
    data.put("capabilities", startupAuditService.getCapabilities());
    data.put("databaseEnabled", databaseEnabled);
    data.put("databaseKind", databaseKind);
    data.put("mysqlEnabled", databaseEnabled && "mysql".equals(databaseKind));
    data.put("postgresEnabled", databaseEnabled && "postgres".equals(databaseKind));
    return data;
  }

  private List<String> sanitizeIssues(List<String> issues) {
    if (issues.isEmpty()) {
      return issues;
    }
    if (properties.getSecurity().isHealthIssueDetailsEnabled()) {
      return issues;
    }
    return List.of("RUNTIME_CONFIG_INCOMPLETE");
  }
}
