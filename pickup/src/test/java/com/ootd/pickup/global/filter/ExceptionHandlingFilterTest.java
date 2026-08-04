package com.ootd.pickup.global.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ootd.pickup.auth.token.InvalidAccessTokenException;
import com.ootd.pickup.auth.token.InvalidAdminAccessTokenException;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.auth.AdminTokenCookieManager;
import com.ootd.pickup.global.auth.TokenCookieManager;
import com.ootd.pickup.global.auth.TokenCookieProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ExceptionHandlingFilterTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private final TokenCookieManager tokenCookieManager =
      new TokenCookieManager(
          new JwtTokenProperties("pickup", "secret", Duration.ofMinutes(30), Duration.ofDays(14)),
          new TokenCookieProperties(true, "None"));
  private final AdminTokenCookieManager adminTokenCookieManager =
      new AdminTokenCookieManager(new TokenCookieProperties(true, "None"));
  private final ExceptionHandlingFilter exceptionHandlingFilter =
      new ExceptionHandlingFilter(objectMapper, tokenCookieManager, adminTokenCookieManager);

  @Test
  void 액세스_토큰이_유효하지_않으면_401_응답을_반환한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    doThrow(new InvalidAccessTokenException()).when(filterChain).doFilter(request, response);

    // when
    exceptionHandlingFilter.doFilter(request, response, filterChain);

    // then
    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(body.get("error").asText()).isEqualTo("INVALID_ACCESS_TOKEN");
    assertThat(body.get("message").asText()).isEqualTo("유효하지 않은 액세스 토큰입니다.");
    assertThat(body.get("path").asText()).isEqualTo("/members/me");
    assertThat(body.get("timestamp").asText()).isNotBlank();
  }

  @Test
  void 액세스_토큰이_유효하지_않으면_액세스_토큰_쿠키를_만료시킨다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    doThrow(new InvalidAccessTokenException()).when(filterChain).doFilter(request, response);

    // when
    exceptionHandlingFilter.doFilter(request, response, filterChain);

    // then
    Cookie accessTokenCookie = response.getCookie("access-token");
    assertThat(accessTokenCookie).isNotNull();
    assertThat(accessTokenCookie.getValue()).isEmpty();
    assertThat(accessTokenCookie.getMaxAge()).isZero();
    assertThat(accessTokenCookie.getPath()).isEqualTo("/");
  }

  @Test
  void 액세스_토큰이_유효하지_않아도_리프레시_토큰_쿠키는_만료시키지_않는다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    doThrow(new InvalidAccessTokenException()).when(filterChain).doFilter(request, response);

    // when
    exceptionHandlingFilter.doFilter(request, response, filterChain);

    // then
    assertThat(response.getCookie("refresh-token")).isNull();
  }

  @Test
  void 관리자_액세스_토큰이_유효하지_않으면_401_응답을_반환하고_관리자_토큰_쿠키를_만료시킨다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/members");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    doThrow(new InvalidAdminAccessTokenException()).when(filterChain).doFilter(request, response);

    // when
    exceptionHandlingFilter.doFilter(request, response, filterChain);

    // then
    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(body.get("error").asText()).isEqualTo("INVALID_ADMIN_ACCESS_TOKEN");

    Cookie adminAccessTokenCookie = response.getCookie("admin-access-token");
    assertThat(adminAccessTokenCookie).isNotNull();
    assertThat(adminAccessTokenCookie.getValue()).isEmpty();
    assertThat(adminAccessTokenCookie.getMaxAge()).isZero();
  }

  @Test
  void 예상하지_못한_예외가_발생하면_그대로_전파한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members/me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    doThrow(new IllegalStateException("unexpected")).when(filterChain).doFilter(request, response);

    // when & then
    assertThatThrownBy(() -> exceptionHandlingFilter.doFilter(request, response, filterChain))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unexpected");
  }
}
