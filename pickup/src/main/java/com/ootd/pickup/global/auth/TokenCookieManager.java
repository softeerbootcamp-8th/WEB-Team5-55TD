package com.ootd.pickup.global.auth;

import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenCookieManager {
  private final JwtTokenProperties jwtTokenProperties;
  private final TokenCookieProperties tokenCookieProperties;
  private final CsrfTokenGenerator csrfTokenGenerator;

  public HttpHeaders createTokenCookieHeaders(AccessToken accessToken, String refreshToken) {
    HttpHeaders headers = new HttpHeaders();
    addCookie(
        headers,
        AuthenticationAttributes.COOKIE_NAME,
        accessToken.value(),
        "/",
        jwtTokenProperties.accessTokenTtl(),
        true);
    addCookie(
        headers,
        AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
        refreshToken,
        "/auth",
        jwtTokenProperties.refreshTokenTtl(),
        true);
    // CSRF 쿠키는 리프레시 토큰과 생명주기를 맞춘다 — 액세스 토큰이 만료돼 갱신하는 순간까지도
    // /auth/refresh 요청 자체가 CSRF 검증 대상이라, 그보다 먼저 CSRF 쿠키가 사라지면 안 된다.
    addCookie(
        headers,
        AuthenticationAttributes.CSRF_TOKEN_COOKIE_NAME,
        csrfTokenGenerator.generate(),
        "/",
        jwtTokenProperties.refreshTokenTtl(),
        false);
    return headers;
  }

  public HttpHeaders createExpiredTokenCookieHeaders() {
    HttpHeaders headers = createExpiredAccessTokenCookieHeaders();
    addCookie(
        headers,
        AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
        "",
        "/auth",
        Duration.ZERO,
        true);
    addCookie(
        headers, AuthenticationAttributes.CSRF_TOKEN_COOKIE_NAME, "", "/", Duration.ZERO, false);
    return headers;
  }

  /** 액세스 토큰 쿠키만 만료시킨다. 리프레시 토큰 쿠키는 유지해야 액세스 토큰 만료 시 갱신 흐름이 이어진다. */
  public HttpHeaders createExpiredAccessTokenCookieHeaders() {
    HttpHeaders headers = new HttpHeaders();
    addCookie(headers, AuthenticationAttributes.COOKIE_NAME, "", "/", Duration.ZERO, true);
    return headers;
  }

  private void addCookie(
      HttpHeaders headers,
      String name,
      String value,
      String path,
      Duration maxAge,
      boolean httpOnly) {
    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(httpOnly)
            .secure(tokenCookieProperties.secure())
            .sameSite(tokenCookieProperties.sameSite())
            .path(path)
            .maxAge(maxAge)
            .build();

    headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
