package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.gaokao.essay.backend.config.GaokaoProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HealthServiceTest {

  @Test
  void shouldExposeDatabaseStateEvenWhenHealthDetailsFlagIsOff() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getStorage().getDatabase().setEnabled(true);
    properties.getStorage().getDatabase().setUrl("jdbc:postgresql://db.example.com:5432/postgres");

    StartupAuditService startupAuditService = Mockito.mock(StartupAuditService.class);
    when(startupAuditService.getLastIssues()).thenReturn(List.of());
    when(startupAuditService.isReviewReady()).thenReturn(true);
    when(startupAuditService.getCapabilities()).thenReturn(Map.of("storageMode", "postgres"));

    HealthService service = new HealthService(properties, startupAuditService);
    Map<String, Object> health = service.buildHealth();

    assertEquals(true, health.get("databaseEnabled"));
    assertEquals("postgres", health.get("databaseKind"));
    assertEquals(true, health.get("postgresEnabled"));
    assertTrue(health.containsKey("capabilities"));
  }
}
