package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.security.InMemoryAbuseProtectionStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestSecurityServiceTest {
  @Test
  void rejectsInvalidDeviceId() {
    RequestSecurityService service = service();
    ApiException error = assertThrows(
        ApiException.class,
        () -> service.checkEssaySubmission(request("203.0.113.10"), "user-a", "short")
    );
    assertEquals("DEVICE_ID_REQUIRED", error.getCode());
  }

  @Test
  void limitsEssayByUserDeviceAndIp() {
    RequestSecurityService service = service();
    for (int index = 0; index < 5; index += 1) {
      service.checkEssaySubmission(
          request("203.0.113.10"),
          "user-a",
          "device_1234567890"
      );
    }
    ApiException error = assertThrows(
        ApiException.class,
        () -> service.checkEssaySubmission(
            request("203.0.113.10"),
            "user-a",
            "device_1234567890"
        )
    );
    assertEquals("RATE_LIMITED", error.getCode());
  }

  private RequestSecurityService service() {
    return new RequestSecurityService(new GaokaoProperties(), new InMemoryAbuseProtectionStore());
  }

  private MockHttpServletRequest request(String ip) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(ip);
    return request;
  }
}
