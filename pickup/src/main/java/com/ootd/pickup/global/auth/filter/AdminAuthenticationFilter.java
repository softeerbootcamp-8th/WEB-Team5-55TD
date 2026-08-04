package com.ootd.pickup.global.auth.filter;

import com.ootd.pickup.auth.token.AdminAccessTokenVerifier;
import com.ootd.pickup.global.auth.AdminAuthentication;
import com.ootd.pickup.global.auth.AdminAuthenticationAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

  private final AdminAccessTokenVerifier adminAccessTokenVerifier;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().startsWith("/admin");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional<String> accessToken =
        getCookieValue(request, AdminAuthenticationAttributes.COOKIE_NAME);

    if (accessToken.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    AdminAuthentication authentication = adminAccessTokenVerifier.verify(accessToken.get());
    request.setAttribute(AdminAuthenticationAttributes.ATTRIBUTE_NAME, authentication);

    filterChain.doFilter(request, response);
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
