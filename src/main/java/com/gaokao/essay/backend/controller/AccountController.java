package com.gaokao.essay.backend.controller;

import com.gaokao.essay.backend.model.ApiResponse;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.service.MembershipService;
import com.gaokao.essay.backend.service.GrowthProfileService;
import com.gaokao.essay.backend.service.SessionService;
import com.gaokao.essay.backend.service.StudyProfileService;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final SessionService sessionService;
  private final MembershipService membershipService;
  private final StudyProfileService studyProfileService;
  private final GrowthProfileService growthProfileService;

  public AccountController(
      SessionService sessionService,
      MembershipService membershipService,
      StudyProfileService studyProfileService,
      GrowthProfileService growthProfileService
  ) {
    this.sessionService = sessionService;
    this.membershipService = membershipService;
    this.studyProfileService = studyProfileService;
    this.growthProfileService = growthProfileService;
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
}
