package com.gaokao.essay.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.model.ApiResponse;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.EssayTaskRequest;
import com.gaokao.essay.backend.model.ModelEssayResult;
import com.gaokao.essay.backend.service.EssayService;
import com.gaokao.essay.backend.service.ChallengeService;
import com.gaokao.essay.backend.service.RequestSecurityService;
import com.gaokao.essay.backend.service.SessionService;
import com.gaokao.essay.backend.service.WechatService;
import com.gaokao.essay.backend.service.ModelEssayService;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/gaokao-essay")
public class EssayController {

  private final EssayService essayService;
  private final SessionService sessionService;
  private final WechatService wechatService;
  private final RequestSecurityService requestSecurityService;
  private final ChallengeService challengeService;
  private final ObjectMapper objectMapper;
  private final com.gaokao.essay.backend.service.HistoryService historyService;
  private final ModelEssayService modelEssayService;

  public EssayController(
      EssayService essayService,
      SessionService sessionService,
      WechatService wechatService,
      RequestSecurityService requestSecurityService,
      ChallengeService challengeService,
      ObjectMapper objectMapper,
      com.gaokao.essay.backend.service.HistoryService historyService,
      ModelEssayService modelEssayService
  ) {
    this.essayService = essayService;
    this.sessionService = sessionService;
    this.wechatService = wechatService;
    this.requestSecurityService = requestSecurityService;
    this.challengeService = challengeService;
    this.objectMapper = objectMapper;
    this.historyService = historyService;
    this.modelEssayService = modelEssayService;
  }

  @GetMapping("/challenge")
  public ApiResponse<Map<String, Object>> getChallenge(
      HttpServletRequest servletRequest,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    requestSecurityService.checkChallengeAttempt(servletRequest);
    String userId = resolveChallengeSubject(servletRequest, authorizationHeader);
    String challenge = challengeService.issueChallenge(userId);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("challenge", challenge);
    data.put("ttlSeconds", challengeService.getChallengeTtlSeconds());
    return ApiResponse.ok(data);
  }

  @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<StreamingResponseBody> submitEssayTask(
      HttpServletRequest servletRequest,
      @Valid @RequestBody EssayTaskRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestHeader(value = "X-Challenge", required = false) String challengeHeader,
      @RequestHeader(value = "X-Device-ID", required = false) String deviceId
  ) {
    LoginSession loginSession = resolveLoginSession(servletRequest, authorizationHeader, request.getOpenId(), request.getWxCode());
    String normalizedDeviceId = requestSecurityService.requireDeviceId(deviceId);
    requestSecurityService.checkEssaySubmission(servletRequest, loginSession.user().userId(), normalizedDeviceId);
    challengeService.consumeChallenge(challengeHeader, loginSession.user().userId());
    EssayService.EssayExecution execution = essayService.execute(
        loginSession.user(),
        request,
        normalizedDeviceId,
        RequestSecurityService.resolveClientIpStatic(servletRequest)
    );

    StreamingResponseBody stream = outputStream -> {
      writeEvent(outputStream, "status", "已通过身份校验，正在整理题面...");
      writeEvent(outputStream, "status", "内容安全检查已通过，开始生成结果...");
      for (String chunk : TextUtils.chunkText(execution.getStreamText(), 90)) {
        writeEvent(outputStream, "chunk", chunk);
      }
      writeEvent(outputStream, "meta", buildMetaPayload(execution.getRecord(), loginSession));
      writeEvent(outputStream, "done", "OK");
      outputStream.flush();
    };

    return ResponseEntity.ok()
        .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
        .body(stream);
  }

  @GetMapping("/history")
  public ApiResponse<Map<String, Object>> historyPage(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String essayType,
      @RequestParam(required = false) String taskStatus
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    requestSecurityService.checkHistoryRead(request, user.userId());
    return ApiResponse.ok(historyService.listRecords(user, offset, limit, mode, essayType, taskStatus));
  }

  @GetMapping("/history/{id}")
  public ApiResponse<AppState.EssayRecord> historyDetail(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String id
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    requestSecurityService.checkHistoryRead(request, user.userId());
    return ApiResponse.ok(historyService.getRecord(user, id));
  }

  @PostMapping("/history/{id}/model-essay")
  public ApiResponse<ModelEssayResult> modelEssay(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String id,
      @RequestBody(required = false) Map<String, ?> body
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    requestSecurityService.checkHistoryRead(request, user.userId());
    boolean regenerate = body != null && Boolean.TRUE.equals(body.get("regenerate"));
    return ApiResponse.ok(modelEssayService.getOrGenerate(user, id, regenerate));
  }

  @DeleteMapping("/history/{id}")
  public ApiResponse<Map<String, Object>> deleteHistoryItem(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @PathVariable String id
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    requestSecurityService.checkHistoryRead(request, user.userId());
    return ApiResponse.ok(historyService.deleteRecord(user, id));
  }

  @DeleteMapping("/history")
  public ApiResponse<Map<String, Object>> clearHistory(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String essayType,
      @RequestParam(required = false) String taskStatus
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    requestSecurityService.checkHistoryRead(request, user.userId());
    return ApiResponse.ok(historyService.clearRecords(user, mode, essayType, taskStatus));
  }

  private String resolveChallengeSubject(
      HttpServletRequest request,
      String authorizationHeader
  ) {
    AuthenticatedUser tokenUser = sessionService.resolveUserByToken(extractBearerToken(authorizationHeader));
    if (tokenUser != null) {
      return tokenUser.userId();
    }
    AuthenticatedUser attrUser = sessionService.currentUser(request);
    if (attrUser != null) {
      return attrUser.userId();
    }
    return RequestSecurityService.resolveClientIpStatic(request);
  }

  private LoginSession resolveLoginSession(
      HttpServletRequest request,
      String authorizationHeader,
      String requestOpenId,
      String wxCode
  ) {
    AuthenticatedUser tokenUser = sessionService.resolveUserByToken(extractBearerToken(authorizationHeader));
    if (tokenUser != null) {
      return new LoginSession(tokenUser, "", tokenUser.expiresAt().getEpochSecond());
    }
    if (!TextUtils.isBlank(wxCode)) {
      String openId = wechatService.resolveOpenId(wxCode);
      AppState.SessionToken session = sessionService.issueSession(openId);
      AuthenticatedUser authenticatedUser = new AuthenticatedUser(
          session.userId,
          session.openId,
          Instant.now(),
          Instant.ofEpochSecond(session.expiresAtEpochSeconds)
      );
      return new LoginSession(authenticatedUser, session.token, session.expiresAtEpochSeconds);
    }
    AuthenticatedUser fallbackUser = sessionService.requireUser(request, authorizationHeader, requestOpenId);
    long expiresAt = fallbackUser.expiresAt() == null ? 0L : fallbackUser.expiresAt().getEpochSecond();
    return new LoginSession(fallbackUser, "", expiresAt);
  }

  private String buildMetaPayload(AppState.EssayRecord record, LoginSession loginSession) throws IOException {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", record.id);
    data.put("clientRequestId", record.clientRequestId);
    data.put("content", record.content);
    data.put("wordCount", record.wordCount);
    data.put("scoreText", record.scoreText);
    data.put("coachPlan", record.coachPlan);
    data.put("analysis", record.analysis);
    data.put("userId", loginSession.user().userId());
    if (!TextUtils.isBlank(loginSession.token())) {
      data.put("token", loginSession.token());
      data.put("openId", loginSession.user().openId());
      data.put("expiresAtEpochSeconds", loginSession.expiresAtEpochSeconds());
    }
    return objectMapper.writeValueAsString(data);
  }

  private void writeEvent(OutputStream outputStream, String eventName, String content) throws IOException {
    String payload = "event: " + eventName + "\n" + "data: " + content + "\n\n";
    outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
    outputStream.flush();
  }

  private String extractBearerToken(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      return "";
    }
    if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return "";
    }
    return authorizationHeader.substring(7).trim();
  }

  private record LoginSession(AuthenticatedUser user, String token, long expiresAtEpochSeconds) {
  }
}
