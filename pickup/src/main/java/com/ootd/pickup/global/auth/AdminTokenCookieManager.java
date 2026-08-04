package com.ootd.pickup.global.auth;

import com.ootd.pickup.auth.token.AccessToken;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminTokenCookieManager {
  private final TokenCookieProperties tokenCookieProperties;

  public HttpHeaders createTokenCookieHeaders(AccessToken accessToken) {
    HttpHeaders headers = new HttpHeaders();
    addCookie(
        headers,
        AdminAuthenticationAttributes.COOKIE_NAME,
        accessToken.value(),
        Duration.between(java.time.Instant.now(), accessToken.expiresAt()));
    return headers;
  }

  public HttpHeaders createExpiredTokenCookieHeaders() {
    HttpHeaders headers = new HttpHeaders();
    addCookie(headers, AdminAuthenticationAttributes.COOKIE_NAME, "", Duration.ZERO);
    return headers;
  }

  private void addCookie(HttpHeaders headers, String name, String value, Duration maxAge) {
    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(tokenCookieProperties.secure())
            .sameSite(tokenCookieProperties.sameSite())
            .path("/admin")
            .maxAge(maxAge)
            .build();

    headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
