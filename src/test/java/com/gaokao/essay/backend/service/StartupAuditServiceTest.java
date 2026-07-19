package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.gaokao.essay.backend.config.GaokaoProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

class StartupAuditServiceTest {

  @Test
  void shouldRejectStateFileStorageInStrictMode() {
    GaokaoProperties properties = baseProperties();
    properties.getRuntime().setStrictStartupChecks(true);
    properties.getStorage().getDatabase().setEnabled(false);

    StartupAuditService service = new StartupAuditService(
        properties,
        readyAiGatewayService(),
        disabledOcrService(),
        readyWechatService(),
        readyWechatPayService()
    );

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> service.run(new DefaultApplicationArguments(new String[0])));

    assertEquals("Startup audit failed with 1 issue(s). Check logs for details.", error.getMessage());
    assertFalse(service.isReviewReady());
    assertEquals(1, service.getLastIssues().size());
  }

  @Test
  void shouldReportConfiguredButUnreadyOcr() {
    GaokaoProperties properties = baseProperties();

    OcrService ocrService = Mockito.mock(OcrService.class);
    when(ocrService.isEnabled()).thenReturn(true);
    when(ocrService.isReady()).thenReturn(false);

    StartupAuditService service = new StartupAuditService(
        properties,
        readyAiGatewayService(),
        ocrService,
        readyWechatService(),
        readyWechatPayService()
    );

    service.run(new DefaultApplicationArguments(new String[0]));

    assertFalse(service.isReviewReady());
    assertEquals("OCR is enabled, but OCR upstream configuration is incomplete.", service.getLastIssues().get(0));
    Map<String, Object> capabilities = service.getCapabilities();
    assertEquals(true, capabilities.get("ocrEnabled"));
    assertEquals("configured-but-unready", capabilities.get("ocrMode"));
  }

  private GaokaoProperties baseProperties() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.setAuthTokenSecret("prod-secret-value-1234567890123456");
    properties.getStorage().getDatabase().setEnabled(true);
    properties.getStorage().getDatabase().setUrl("jdbc:postgresql://db.example.com:5432/postgres");
    properties.getStorage().getDatabase().setUsername("postgres");
    properties.getSecurity().setMsgSecEnabled(true);
    properties.getKnowledge().setEnabled(true);
    properties.getMembership().setAllowDebugSubscriptionActivate(false);
    return properties;
  }

  private AiGatewayService readyAiGatewayService() {
    AiGatewayService service = Mockito.mock(AiGatewayService.class);
    when(service.isTextGenerationReady()).thenReturn(true);
    when(service.isVisionReady()).thenReturn(true);
    when(service.getVisionProviderLabel()).thenReturn("vision-ocr");
    return service;
  }

  private OcrService disabledOcrService() {
    OcrService service = Mockito.mock(OcrService.class);
    when(service.isEnabled()).thenReturn(false);
    when(service.isReady()).thenReturn(false);
    return service;
  }

  private WechatService readyWechatService() {
    WechatService service = Mockito.mock(WechatService.class);
    when(service.hasCode2SessionConfig()).thenReturn(true);
    return service;
  }

  private WechatPayService readyWechatPayService() {
    WechatPayService service = Mockito.mock(WechatPayService.class);
    when(service.isReady()).thenReturn(true);
    return service;
  }
}
