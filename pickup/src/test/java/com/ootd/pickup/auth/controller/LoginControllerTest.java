package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponse;
import com.ootd.pickup.auth.service.AuthService;
import com.ootd.pickup.auth.service.LoginResult;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.auth.TokenCookieManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        JwtTokenProperties tokenProperties = new JwtTokenProperties(
                "pickup-test",
                "secret",
                Duration.ofMinutes(15),
                Duration.ofDays(14)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(
                        authService,
                        new TokenCookieManager(tokenProperties)
                )
        ).build();
    }

    @Test
    void 로그인에_성공하면_두_토큰을_보안_쿠키로_전달한다() throws Exception {
        // given
        LoginRequest request = new LoginRequest("pickup-user", "password1234");
        LoginResponse response = new LoginResponse(1L, "pickup-user", "픽업회원", null);
        LoginResult result = new LoginResult(
                response,
                new AccessToken("access-token", Instant.now().plusSeconds(900)),
                "refresh-token"
        );
        given(authService.login(request)).willReturn(result);

        // when & then
        mockMvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"pickup-user\",\"password\":\"password1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1L))
                .andExpect(jsonPath("$.loginId").value("pickup-user"))
                .andExpect(header().stringValues(
                        HttpHeaders.SET_COOKIE,
                        contains(
                                allOf(
                                        containsString("access-token=access-token"),
                                        containsString("HttpOnly"),
                                        containsString("Secure"),
                                        containsString("SameSite=None")
                                ),
                                allOf(
                                        containsString("refresh-token=refresh-token"),
                                        containsString("HttpOnly"),
                                        containsString("Secure"),
                                        containsString("SameSite=None")
                                )
                        )
                ));
    }

    @Test
    void 아이디나_비밀번호가_4자_미만이면_로그인하지_않는다() throws Exception {
        // given
        String requestBody = "{\"loginId\":\"abc\",\"password\":\"123\"}";

        // when
        ResultActions result = mockMvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody));

        // then
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }
}
