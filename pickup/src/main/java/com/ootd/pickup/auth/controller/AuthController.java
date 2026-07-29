package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.api.AuthApi;
import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponseBody;
import com.ootd.pickup.auth.dto.RefreshResponseBody;
import com.ootd.pickup.auth.service.AuthService;
import com.ootd.pickup.auth.service.LoginResponse;
import com.ootd.pickup.auth.service.RefreshResponse;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.TokenCookieManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {
  private final AuthService authService;
  private final TokenCookieManager tokenCookieManager;

  @PostMapping
  @Override
  public ResponseEntity<LoginResponseBody> login(@Valid @RequestBody LoginRequest loginRequest) {
    LoginResponse response = authService.login(loginRequest);
    return ResponseEntity.ok()
        .headers(
            tokenCookieManager.createTokenCookieHeaders(
                response.accessToken(), response.refreshToken()))
        .body(response.body());
  }

  @PostMapping("/refresh")
  @Override
  public ResponseEntity<RefreshResponseBody> refresh(
      @CookieValue(name = AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME, required = false)
          String refreshToken) {
    RefreshResponse response = authService.refresh(refreshToken);

    return ResponseEntity.ok()
        .headers(
            tokenCookieManager.createTokenCookieHeaders(
                response.accessToken(), response.refreshToken()))
        .body(response.body());
  }

  @PostMapping("/logout")
  @Override
  public ResponseEntity<Void> logout(
      @CookieValue(name = AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME, required = false)
          String refreshToken) {
    authService.logout(refreshToken);

    return ResponseEntity.noContent()
        .headers(tokenCookieManager.createExpiredTokenCookieHeaders())
        .build();
  }
}
