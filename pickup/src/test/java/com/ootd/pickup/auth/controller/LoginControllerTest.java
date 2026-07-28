package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponse;
import com.ootd.pickup.auth.service.LoginResult;
import com.ootd.pickup.auth.service.LoginService;
import com.ootd.pickup.auth.service.LogoutService;
import com.ootd.pickup.auth.service.RefreshTokenService;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.global.auth.TokenCookieManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerTest {

    private LoginService loginService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        JwtTokenProperties tokenProperties = new JwtTokenProperties(
                "pickup-test",
                "secret",
                Duration.ofMinutes(15),
                Duration.ofDays(14)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(
                        loginService,
                        mock(RefreshTokenService.class),
                        mock(LogoutService.class),
                        new TokenCookieManager(tokenProperties)
                )
        ).build();
    }

    @Test
    void 로그인_성공_시_두_토큰을_HttpOnly_쿠키로_전달한다() throws Exception {
        LoginRequest request = new LoginRequest("pickup-user", "password1234");
        LoginResponse response = new LoginResponse(1L, "pickup-user", "픽업회원", null);
        LoginResult result = new LoginResult(
                response,
                new AccessToken("access-token", Instant.now().plusSeconds(900)),
                "refresh-token"
        );
        given(loginService.login(request)).willReturn(result);

        mockMvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"pickup-user\",\"password\":\"password1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1L))
                .andExpect(jsonPath("$.loginId").value("pickup-user"))
                .andExpect(header().stringValues(
                        HttpHeaders.SET_COOKIE,
                        hasItems(
                                containsString("access-token=access-token"),
                                containsString("refresh-token=refresh-token"),
                                containsString("HttpOnly"),
                                containsString("Secure"),
                                containsString("SameSite=None")
                        )
                ));
    }

    @Test
    void 아이디나_비밀번호가_4자_미만이면_로그인하지_않는다() throws Exception {
        mockMvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"abc\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(loginService);
    }
}
