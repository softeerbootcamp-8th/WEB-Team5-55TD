package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.service.LogoutService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogoutControllerTest {
    private LogoutService logoutService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        logoutService = mock(LogoutService.class);
        JwtTokenProperties tokenProperties = new JwtTokenProperties(
                "pickup-test",
                "secret",
                Duration.ofMinutes(15),
                Duration.ofDays(14)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new LogoutController(
                        logoutService,
                        new TokenCookieManager(tokenProperties)
                )
        ).build();
    }

    @Test
    void 로그아웃하면_리프레시_토큰을_폐기하고_두_쿠키를_만료한다() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie(
                                AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
                                "refresh-token"
                        )))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().stringValues(
                        HttpHeaders.SET_COOKIE,
                        contains(
                                allOf(
                                        containsString("access-token="),
                                        containsString("Path=/;"),
                                        containsString("Max-Age=0"),
                                        containsString("HttpOnly"),
                                        containsString("Secure"),
                                        containsString("SameSite=None")
                                ),
                                allOf(
                                        containsString("refresh-token="),
                                        containsString("Path=/auth;"),
                                        containsString("Max-Age=0"),
                                        containsString("HttpOnly"),
                                        containsString("Secure"),
                                        containsString("SameSite=None")
                                )
                        )
                ));

        then(logoutService).should().logout("refresh-token");
    }

    @Test
    void 리프레시_토큰이_없어도_로그아웃에_성공한다() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());

        then(logoutService).should().logout(null);
    }
}
