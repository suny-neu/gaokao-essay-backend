package com.gaokao.essay.backend.controller;

import com.gaokao.essay.backend.model.ApiResponse;
import com.gaokao.essay.backend.service.ChallengeService;
import com.gaokao.essay.backend.service.OcrService;
import com.gaokao.essay.backend.service.RequestSecurityService;
import com.gaokao.essay.backend.service.SessionService;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

  private final SessionService sessionService;
  private final OcrService ocrService;
  private final RequestSecurityService requestSecurityService;
  private final ChallengeService challengeService;

  public OcrController(SessionService sessionService, OcrService ocrService, RequestSecurityService requestSecurityService, ChallengeService challengeService) {
    this.sessionService = sessionService;
    this.ocrService = ocrService;
    this.requestSecurityService = requestSecurityService;
    this.challengeService = challengeService;
  }

  @PostMapping("/extract")
  public ApiResponse<Map<String, Object>> extract(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestHeader(value = "X-Challenge", required = false) String challengeHeader,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "scene", required = false) String scene
  ) {
    String openId = sessionService.requireOpenId(authorizationHeader, null);
    String userId = sessionService.requireUser(request, authorizationHeader, null).userId();
    requestSecurityService.checkOcr(request, userId);
    challengeService.consumeChallenge(challengeHeader, userId);
    return ApiResponse.ok(ocrService.extractText(openId, file, scene));
  }
}
