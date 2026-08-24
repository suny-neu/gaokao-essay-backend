package com.gaokao.essay.backend.controller;

import com.gaokao.essay.backend.model.ApiResponse;
import com.gaokao.essay.backend.model.AdRewardClaimRequest;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.DeleteAccountRequest;
import com.gaokao.essay.backend.service.AccountDeletionService;
import com.gaokao.essay.backend.service.MembershipService;
import com.gaokao.essay.backend.service.GrowthProfileService;
import com.gaokao.essay.backend.service.DashboardService;
import com.gaokao.essay.backend.service.SessionService;
import com.gaokao.essay.backend.service.StudyProfileService;
import com.gaokao.essay.backend.util.TextUtils;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final SessionService sessionService;
  private final MembershipService membershipService;
  private final StudyProfileService studyProfileService;
  private final GrowthProfileService growthProfileService;
  private final DashboardService dashboardService;
  private final AccountDeletionService accountDeletionService;

  public AccountController(
      SessionService sessionService,
      MembershipService membershipService,
      StudyProfileService studyProfileService,
      GrowthProfileService growthProfileService,
      DashboardService dashboardService,
      AccountDeletionService accountDeletionService
  ) {
    this.sessionService = sessionService;
    this.membershipService = membershipService;
    this.studyProfileService = studyProfileService;
    this.growthProfileService = growthProfileService;
    this.dashboardService = dashboardService;
    this.accountDeletionService = accountDeletionService;
  }

  @GetMapping("/entitlement")
  public ApiResponse<Map<String, Object>> entitlement(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    return ApiResponse.ok(membershipService.getEntitlement(user));
  }

  @GetMapping("/study-profile")
  public ApiResponse<Map<String, Object>> studyProfile(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(defaultValue = "application") String essayType
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    Map<String, Object> data = new LinkedHashMap<>(studyProfileService.buildStudyProfile(user.userId()));
    data.put("growth", growthProfileService.load(user.userId(), essayType));
    return ApiResponse.ok(data);
  }

  @GetMapping("/dashboard")
  public ApiResponse<Map<String, Object>> dashboard(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestParam(defaultValue = "application") String essayType
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    return ApiResponse.ok(dashboardService.build(user, essayType));
  }

  @PostMapping("/ad-reward/grant")
  public ApiResponse<Map<String, Object>> grantAdReward(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader,
      @org.springframework.web.bind.annotation.RequestBody(required = false) AdRewardClaimRequest claimRequest
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    String deviceId = TextUtils.trimToEmpty(deviceIdHeader);
    String clientIp = TextUtils.trimToEmpty(request.getRemoteAddr());
    String nonce = claimRequest == null ? "" : TextUtils.trimToEmpty(claimRequest.getNonce());
    return ApiResponse.ok(membershipService.grantAdReward(user, deviceId, clientIp, nonce));
  }

  @PostMapping("/ad-reward/session")
  public ApiResponse<Map<String, Object>> createAdRewardSession(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    return ApiResponse.ok(membershipService.createAdRewardSession(user, TextUtils.trimToEmpty(deviceIdHeader)));
  }

  @DeleteMapping
  public ApiResponse<Map<String, Object>> deleteAccount(
      HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody(required = false) DeleteAccountRequest deleteRequest
  ) {
    AuthenticatedUser user = sessionService.requireUser(request, authorizationHeader, null);
    accountDeletionService.deleteAccount(user, deleteRequest == null ? "" : deleteRequest.getConfirmation());
    return ApiResponse.ok(Map.of("deleted", true));
  }
}
