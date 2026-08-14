package com.ootd.pickup.global.auth.filter;

import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.CsrfTokenMismatchException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 이중 제출 쿠키(double-submit cookie) 방식으로 CSRF를 방어한다. 로그인 시 발급한 CSRF 쿠키 값과 요청 헤더 값이 같은지만 확인한다 — 공격 페이지는
 * 피해자의 쿠키를 읽을 수 없으므로 헤더에 같은 값을 실어 보낼 수 없다.
 *
 * <p>CSRF 쿠키가 아직 없는 요청(로그인·회원가입 등 세션이 시작되기 전)은 검증 없이 통과시킨다. 지킬 세션 자체가 없기 때문이다.
 */
public class CsrfProtectionFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SAFE_METHODS.contains(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    Optional<String> csrfCookieValue =
        getCookieValue(request, AuthenticationAttributes.CSRF_TOKEN_COOKIE_NAME);
    if (csrfCookieValue.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    String headerValue = request.getHeader(AuthenticationAttributes.CSRF_TOKEN_HEADER_NAME);
    if (headerValue == null || !constantTimeEquals(csrfCookieValue.get(), headerValue)) {
      throw new CsrfTokenMismatchException();
    }

    filterChain.doFilter(request, response);
  }

  private boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private Optional<String> getCookieValue(HttpServletRequest request, String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        return Optional.of(cookie.getValue());
      }
    }
    return Optional.empty();
  }
}
