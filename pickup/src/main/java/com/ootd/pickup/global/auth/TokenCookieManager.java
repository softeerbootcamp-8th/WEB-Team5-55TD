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

  public HttpHeaders createTokenCookieHeaders(AccessToken accessToken, String refreshToken) {
    HttpHeaders headers = new HttpHeaders();
    addCookie(
        headers,
        AuthenticationAttributes.COOKIE_NAME,
        accessToken.value(),
        "/",
        jwtTokenProperties.accessTokenTtl());
    addCookie(
        headers,
        AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
        refreshToken,
        "/auth",
        jwtTokenProperties.refreshTokenTtl());
    return headers;
  }

  public HttpHeaders createExpiredTokenCookieHeaders() {
    HttpHeaders headers = createExpiredAccessTokenCookieHeaders();
    addCookie(
        headers, AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME, "", "/auth", Duration.ZERO);
    return headers;
  }

  /** 액세스 토큰 쿠키만 만료시킨다. 리프레시 토큰 쿠키는 유지해야 액세스 토큰 만료 시 갱신 흐름이 이어진다. */
  public HttpHeaders createExpiredAccessTokenCookieHeaders() {
    HttpHeaders headers = new HttpHeaders();
    addCookie(headers, AuthenticationAttributes.COOKIE_NAME, "", "/", Duration.ZERO);
    return headers;
  }

  private void addCookie(
      HttpHeaders headers, String name, String value, String path, Duration maxAge) {
    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(tokenCookieProperties.secure())
            .sameSite(tokenCookieProperties.sameSite())
            .path(path)
            .maxAge(maxAge)
            .build();

    headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
