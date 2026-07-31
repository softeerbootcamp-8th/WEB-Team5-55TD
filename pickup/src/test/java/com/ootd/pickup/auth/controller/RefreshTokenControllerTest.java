package com.ootd.pickup.auth.controller;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.contains;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auth.dto.RefreshResponseBody;
import com.ootd.pickup.auth.service.AuthService;
import com.ootd.pickup.auth.service.RefreshResponse;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.TokenCookieManager;
import com.ootd.pickup.global.auth.TokenCookieProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RefreshTokenControllerTest {
  private AuthService authService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    JwtTokenProperties tokenProperties =
        new JwtTokenProperties(
            "pickup-test", "secret", Duration.ofMinutes(15), Duration.ofDays(14));
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AuthController(
                    authService,
                    new TokenCookieManager(
                        tokenProperties, new TokenCookieProperties(true, "None"))))
            .build();
  }

  @Test
  void 재발급에_성공하면_두_토큰을_쿠키로_전달한다() throws Exception {
    // given
    Instant expiresAt = Instant.parse("2026-07-26T05:00:00Z");
    given(authService.refresh("old-refresh-token"))
        .willReturn(
            new RefreshResponse(
                new RefreshResponseBody(expiresAt),
                new AccessToken("new-access-token", expiresAt),
                "new-refresh-token"));

    // when & then
    mockMvc
        .perform(
            post("/auth/refresh")
                .cookie(
                    new Cookie(
                        AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME, "old-refresh-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
        .andExpect(jsonPath("$.accessToken").doesNotExist())
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE,
                    contains(
                        allOf(
                            containsString("access-token=new-access-token"),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("SameSite=None")),
                        allOf(
                            containsString("refresh-token=new-refresh-token"),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("SameSite=None")))));
  }
}
