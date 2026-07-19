package com.gaokao.essay.backend.security;

import com.gaokao.essay.backend.model.AuthenticatedUser;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

  public static final String AUTH_USER_ATTRIBUTE = "AUTH_USER";

  private final JwtTokenService jwtTokenService;

  public AuthInterceptor(JwtTokenService jwtTokenService) {
    this.jwtTokenService = jwtTokenService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new com.gaokao.essay.backend.model.ApiException(
          org.springframework.http.HttpStatus.UNAUTHORIZED,
          "UNAUTHORIZED",
          "缺少有效的 Bearer Token"
      );
    }
    String token = authorization.substring(7).trim();
    AuthenticatedUser user = jwtTokenService.parse(token);
    request.setAttribute(AUTH_USER_ATTRIBUTE, user);
    return true;
  }
}
