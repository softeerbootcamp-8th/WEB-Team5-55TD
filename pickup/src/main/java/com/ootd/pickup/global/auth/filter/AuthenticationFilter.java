package com.ootd.pickup.global.auth.filter;

import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

  private static final Set<String> ACCESS_TOKEN_AUTHENTICATION_EXCLUDED_PATHS =
      Set.of("/auth/refresh", "/auth/logout");

  private final AccessTokenVerifier accessTokenVerifier;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return ACCESS_TOKEN_AUTHENTICATION_EXCLUDED_PATHS.contains(request.getServletPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional<String> accessToken = getCookieValue(request, AuthenticationAttributes.COOKIE_NAME);

    if (accessToken.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = accessTokenVerifier.verify(accessToken.get());
    request.setAttribute(AuthenticationAttributes.ATTRIBUTE_NAME, authentication);

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
