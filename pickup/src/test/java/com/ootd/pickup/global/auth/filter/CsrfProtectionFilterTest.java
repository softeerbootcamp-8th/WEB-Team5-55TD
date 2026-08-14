package com.ootd.pickup.global.auth.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.CsrfTokenMismatchException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfProtectionFilterTest {

  private final CsrfProtectionFilter csrfProtectionFilter = new CsrfProtectionFilter();

  @Test
  void CSRF_쿠키가_없으면_상태변경_요청도_그대로_통과한다() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auctions/1/bids");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    csrfProtectionFilter.doFilter(request, response, filterChain);

    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  @Test
  void GET_요청은_CSRF_쿠키가_있어도_헤더_없이_통과한다() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auctions/1");
    request.setCookies(new Cookie(AuthenticationAttributes.CSRF_TOKEN_COOKIE_NAME, "csrf-value"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    csrfProtectionFilter.doFilter(request, response, filterChain);

    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  @Test
  void CSRF_쿠키와_헤더가_일치하면_통과한다() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auctions/1/bids");
    request.setCookies(new Cookie(AuthenticationAttributes.CSRF_TOKEN_COOKIE_NAME, "csrf-value"));
    request.addHeader(AuthenticationAttributes.CSRF_TOKEN_HEADER_NAME, "csrf-value");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    csrfProtectionFilter.doFilter(request, response, filterChain);

    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  @Test
  void CSRF_쿠키는_있는데_헤더가_없으면_거부한다() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auctions/1/bids");
    request.setCookies(new Cookie(AuthenticationAttributes.CSRF_TOKEN_COOKIE_NAME, "csrf-value"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    assertThatThrownBy(() -> csrfProtectionFilter.doFilter(request, response, filterChain))
        .isInstanceOf(CsrfTokenMismatchException.class);

    verifyNoInteractions(filterChain);
  }

  @Test
  void CSRF_쿠키와_헤더_값이_다르면_거부한다() {
    MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/watches/1");
    request.setCookies(new Cookie(AuthenticationAttributes.CSRF_TOKEN_COOKIE_NAME, "csrf-value"));
    request.addHeader(AuthenticationAttributes.CSRF_TOKEN_HEADER_NAME, "다른-값");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    assertThatThrownBy(() -> csrfProtectionFilter.doFilter(request, response, filterChain))
        .isInstanceOf(CsrfTokenMismatchException.class);

    verifyNoInteractions(filterChain);
  }
}
