package com.ootd.pickup.auth.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.auth.token.InvalidAccessTokenException;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.filter.AuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthenticationFilterTest {

  @Test
  void 유효한_토큰의_인증_객체를_요청에_저장한다() throws Exception {
    AccessTokenVerifier accessTokenVerifier = mock(AccessTokenVerifier.class);
    AuthenticationFilter authenticationFilter = new AuthenticationFilter(accessTokenVerifier);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members/me");
    request.setCookies(new Cookie(AuthenticationAttributes.COOKIE_NAME, "valid-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();
    Authentication authentication = new Authentication(1L);
    given(accessTokenVerifier.verify("valid-token")).willReturn(authentication);

    authenticationFilter.doFilter(request, response, filterChain);

    assertThat(request.getAttribute(AuthenticationAttributes.ATTRIBUTE_NAME))
        .isSameAs(authentication);
    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  @Test
  void 재발급_API는_유효하지_않은_액세스_토큰이_있어도_통과한다() throws Exception {
    AccessTokenVerifier accessTokenVerifier = mock(AccessTokenVerifier.class);
    AuthenticationFilter authenticationFilter = new AuthenticationFilter(accessTokenVerifier);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/refresh");
    request.setServletPath("/auth/refresh");
    request.setCookies(new Cookie(AuthenticationAttributes.COOKIE_NAME, "expired-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    authenticationFilter.doFilter(request, response, filterChain);

    verifyNoInteractions(accessTokenVerifier);
    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  @Test
  void 로그아웃_API는_유효하지_않은_액세스_토큰이_있어도_통과한다() throws Exception {
    AccessTokenVerifier accessTokenVerifier = mock(AccessTokenVerifier.class);
    AuthenticationFilter authenticationFilter = new AuthenticationFilter(accessTokenVerifier);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/logout");
    request.setServletPath("/auth/logout");
    request.setCookies(new Cookie(AuthenticationAttributes.COOKIE_NAME, "expired-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    authenticationFilter.doFilter(request, response, filterChain);

    verifyNoInteractions(accessTokenVerifier);
    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  @Test
  void 유효하지_않은_토큰의_예외를_전파한다() {
    AccessTokenVerifier accessTokenVerifier = mock(AccessTokenVerifier.class);
    AuthenticationFilter authenticationFilter = new AuthenticationFilter(accessTokenVerifier);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/members");
    request.setCookies(new Cookie(AuthenticationAttributes.COOKIE_NAME, "invalid-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();
    given(accessTokenVerifier.verify("invalid-token")).willThrow(new InvalidAccessTokenException());

    assertThatThrownBy(() -> authenticationFilter.doFilter(request, response, filterChain))
        .isInstanceOf(InvalidAccessTokenException.class);

    assertThat(request.getAttribute(AuthenticationAttributes.ATTRIBUTE_NAME)).isNull();
    assertThat(filterChain.getRequest()).isNull();
  }
}
