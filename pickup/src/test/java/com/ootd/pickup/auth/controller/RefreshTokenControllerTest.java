package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.service.RefreshResult;
import com.ootd.pickup.auth.service.RefreshTokenService;
import com.ootd.pickup.auth.service.LoginService;
import com.ootd.pickup.auth.service.LogoutService;
import com.ootd.pickup.auth.token.GeneratedAccessToken;
import com.ootd.pickup.auth.token.JwtTokenProperties;
import com.ootd.pickup.auth.web.AuthenticationAttributes;
import com.ootd.pickup.auth.web.TokenCookieManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenControllerTest {
    private RefreshTokenService refreshTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        refreshTokenService = mock(RefreshTokenService.class);
        JwtTokenProperties tokenProperties = new JwtTokenProperties(
                "pickup-test",
                "secret",
                Duration.ofMinutes(15),
                Duration.ofDays(14)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(
                        mock(LoginService.class),
                        refreshTokenService,
                        mock(LogoutService.class),
                        new TokenCookieManager(tokenProperties)
                )
        ).build();
    }

    @Test
    void 재발급에_성공하면_두_토큰을_쿠키로_전달한다() throws Exception {
        Instant expiresAt = Instant.parse("2026-07-26T05:00:00Z");
        given(refreshTokenService.refresh("old-refresh-token"))
                .willReturn(new RefreshResult(
                        new GeneratedAccessToken("new-access-token", expiresAt),
                        "new-refresh-token"
                ));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(
                                AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
                                "old-refresh-token"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(header().stringValues(
                        HttpHeaders.SET_COOKIE,
                        hasItems(
                                containsString("access-token=new-access-token"),
                                containsString("refresh-token=new-refresh-token"),
                                containsString("HttpOnly"),
                                containsString("SameSite=None")
                        )
                ));
    }
}
