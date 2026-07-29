package com.ootd.pickup.auth.controller;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.contains;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponseBody;
import com.ootd.pickup.auth.service.AuthService;
import com.ootd.pickup.auth.service.LoginResponse;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.auth.TokenCookieManager;
import com.ootd.pickup.global.auth.TokenCookieProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerTest {

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
  void 로그인에_성공하면_두_토큰을_보안_쿠키로_전달한다() throws Exception {
    // given
    LoginRequest request = new LoginRequest("pickup-user", "password1234");
    LoginResponseBody body = new LoginResponseBody(1L, "pickup-user", "픽업회원", null);
    LoginResponse response =
        new LoginResponse(
            body, new AccessToken("access-token", Instant.now().plusSeconds(900)), "refresh-token");
    given(authService.login(request)).willReturn(response);

    // when & then
    mockMvc
        .perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"pickup-user\",\"password\":\"password1234\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(1L))
        .andExpect(jsonPath("$.loginId").value("pickup-user"))
        .andExpect(jsonPath("$.accessToken").doesNotExist())
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE,
                    contains(
                        allOf(
                            containsString("access-token=access-token"),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("SameSite=None")),
                        allOf(
                            containsString("refresh-token=refresh-token"),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("SameSite=None")))));
  }

  @Test
  void 아이디나_비밀번호가_4자_미만이면_로그인하지_않는다() throws Exception {
    // given
    String requestBody = "{\"loginId\":\"abc\",\"password\":\"123\"}";

    // when
    ResultActions result =
        mockMvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content(requestBody));

    // then
    result.andExpect(status().isBadRequest());
    verifyNoInteractions(authService);
  }
}
