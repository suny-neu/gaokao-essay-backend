package com.gaokao.essay.backend.controller;

import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.ApiResponse;
import com.gaokao.essay.backend.model.AuthLoginRequest;
import com.gaokao.essay.backend.service.RequestSecurityService;
import com.gaokao.essay.backend.service.SessionService;
import com.gaokao.essay.backend.service.WechatService;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final WechatService wechatService;
  private final SessionService sessionService;
  private final RequestSecurityService requestSecurityService;

  public AuthController(WechatService wechatService, SessionService sessionService, RequestSecurityService requestSecurityService) {
    this.wechatService = wechatService;
    this.sessionService = sessionService;
    this.requestSecurityService = requestSecurityService;
  }

  @PostMapping("/wx-login")
  public ApiResponse<Map<String, Object>> wxLogin(HttpServletRequest servletRequest, @Valid @RequestBody AuthLoginRequest request) {
    requestSecurityService.checkAuthAttempt(servletRequest);
    if (request == null || TextUtils.isBlank(request.getCode())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "WX_CODE_REQUIRED", "wx.login code 不能为空");
    }
    String openId = wechatService.resolveOpenId(request.getCode());
    AppState.SessionToken session = sessionService.issueSession(openId);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("token", session.token);
    data.put("userId", session.userId);
    data.put("openId", session.openId);
    data.put("expiresAtEpochSeconds", session.expiresAtEpochSeconds);
    return ApiResponse.ok(data);
  }
}
