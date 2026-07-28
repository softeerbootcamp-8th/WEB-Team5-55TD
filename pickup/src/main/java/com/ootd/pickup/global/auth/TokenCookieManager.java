package com.ootd.pickup.global.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TokenCookieManager {
    private final JwtTokenProperties jwtTokenProperties;
    private final TokenCookieProperties tokenCookieProperties;

    public HttpHeaders createTokenCookieHeaders(
        AccessToken accessToken,
        String refreshToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        addCookie(
            headers,
            AuthenticationAttributes.COOKIE_NAME,
            accessToken.value(),
            "/",
            jwtTokenProperties.accessTokenTtl()
        );
        addCookie(
            headers,
            AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
            refreshToken,
            "/auth",
            jwtTokenProperties.refreshTokenTtl()
        );
        return headers;
    }

    public HttpHeaders createExpiredTokenCookieHeaders() {
        HttpHeaders headers = new HttpHeaders();
        addCookie(
            headers,
            AuthenticationAttributes.COOKIE_NAME,
            "",
            "/",
            Duration.ZERO
        );
        addCookie(
            headers,
            AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
            "",
            "/auth",
            Duration.ZERO
        );
        return headers;
    }

    private void addCookie(
        HttpHeaders headers,
        String name,
        String value,
        String path,
        Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(tokenCookieProperties.secure())
            .sameSite(tokenCookieProperties.sameSite())
            .path(path)
            .maxAge(maxAge)
            .build();

        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
