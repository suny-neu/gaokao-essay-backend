package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.UserBinding;
import com.gaokao.essay.backend.repository.UserBindingRepository;
import com.gaokao.essay.backend.security.AuthInterceptor;
import com.gaokao.essay.backend.security.JwtTokenService;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Instant;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

  private final GaokaoProperties properties;
  private final UserBindingRepository userBindingRepository;
  private final JwtTokenService jwtTokenService;

  public SessionService(
      GaokaoProperties properties,
      UserBindingRepository userBindingRepository,
      JwtTokenService jwtTokenService
  ) {
    this.properties = properties;
    this.userBindingRepository = userBindingRepository;
    this.jwtTokenService = jwtTokenService;
  }

  public AppState.SessionToken issueSession(String openId) {
    UserBinding binding = ensureBinding(openId);
    long expiresAt = Instant.now().getEpochSecond() + jwtTokenService.expiresInSeconds();
    AppState.SessionToken session = new AppState.SessionToken();
    session.token = jwtTokenService.issueToken(binding.userId(), binding.openId());
    session.userId = binding.userId();
    session.openId = binding.openId();
    session.expiresAtEpochSeconds = expiresAt;
    return session;
  }

  public AuthenticatedUser requireUser(HttpServletRequest request, String authorizationHeader, String requestOpenId) {
    AuthenticatedUser currentUser = currentUser(request);
    if (currentUser != null) {
      return currentUser;
    }

    String token = extractBearerToken(authorizationHeader);
    if (!TextUtils.isBlank(token)) {
      return requireActiveBinding(jwtTokenService.parse(token));
    }

    if (!TextUtils.isBlank(requestOpenId) && properties.isRequestOpenIdFallbackEnabled()) {
      return toAuthenticatedUser(ensureBinding(requestOpenId.trim()));
    }

    throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "当前请求缺少有效登录态，请重新进入小程序后再试");
  }

  public AuthenticatedUser resolveUserByToken(String token) {
    if (TextUtils.isBlank(token)) {
      return null;
    }
    try {
      return requireActiveBinding(jwtTokenService.parse(token));
    } catch (ApiException error) {
      return null;
    }
  }

  public String requireOpenId(String authorizationHeader, String requestOpenId) {
    return requireUser(null, authorizationHeader, requestOpenId).openId();
  }

  public String resolveOpenIdByToken(String token) {
    AuthenticatedUser user = resolveUserByToken(token);
    return user == null ? "" : user.openId();
  }

  public UserBinding ensureBinding(String openId) {
    String normalizedOpenId = TextUtils.trimToEmpty(openId);
    if (TextUtils.isBlank(normalizedOpenId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OPENID_REQUIRED", "openId 不能为空");
    }
    Instant now = Instant.now();
    return userBindingRepository.findByOpenId(normalizedOpenId)
        .map(binding -> userBindingRepository.save(new UserBinding(
            binding.userId(),
            binding.openId(),
            binding.createdAt(),
            now
        )))
        .orElseGet(() -> userBindingRepository.save(new UserBinding(
            TextUtils.uid("user"),
            normalizedOpenId,
            now,
            now
        )));
  }

  public AuthenticatedUser currentUser(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object value = request.getAttribute(AuthInterceptor.AUTH_USER_ATTRIBUTE);
    if (value instanceof AuthenticatedUser authenticatedUser) {
      return requireActiveBinding(authenticatedUser);
    }
    return null;
  }

  private AuthenticatedUser toAuthenticatedUser(UserBinding binding) {
    Instant issuedAt = Instant.now();
    return new AuthenticatedUser(
        binding.userId(),
        binding.openId(),
        issuedAt,
        issuedAt.plusSeconds(jwtTokenService.expiresInSeconds())
    );
  }

  private AuthenticatedUser requireActiveBinding(AuthenticatedUser user) {
    UserBinding binding = userBindingRepository.findByOpenId(user.openId())
        .filter(item -> item.userId().equals(user.userId()))
        .orElseThrow(() -> new ApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "当前登录态已失效，请重新进入小程序后再试"
        ));
    return new AuthenticatedUser(user.userId(), binding.openId(), user.issuedAt(), user.expiresAt());
  }

  private String extractBearerToken(String authorizationHeader) {
    if (TextUtils.isBlank(authorizationHeader)) {
      return "";
    }
    String header = authorizationHeader.trim();
    if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return "";
    }
    return header.substring(7).trim();
  }
}
