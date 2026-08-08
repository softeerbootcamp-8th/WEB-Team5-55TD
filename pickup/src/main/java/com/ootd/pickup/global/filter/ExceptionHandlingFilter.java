package com.ootd.pickup.global.filter;

import com.ootd.pickup.auth.token.InvalidAccessTokenException;
import com.ootd.pickup.global.auth.TokenCookieManager;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.ExceptionResponseFactory;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class ExceptionHandlingFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper;
  private final TokenCookieManager tokenCookieManager;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } catch (InvalidAccessTokenException exception) {
      writeInvalidAccessTokenResponse(request, response);
    }
  }

  private void writeInvalidAccessTokenResponse(
      HttpServletRequest request, HttpServletResponse response) throws IOException {
    ExceptionCode exceptionCode = ExceptionCode.INVALID_ACCESS_TOKEN;
    ExceptionResponse body = ExceptionResponseFactory.from(exceptionCode, request.getRequestURI());

    expireAccessTokenCookie(response);
    response.setStatus(exceptionCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), body);
  }

  /** 유효하지 않은 액세스 토큰 쿠키를 만료시킨다. 쿠키를 남겨두면 로그인 요청까지 인증 필터에 막혀 사용자가 스스로 복구할 수 없다. */
  private void expireAccessTokenCookie(HttpServletResponse response) {
    tokenCookieManager
        .createExpiredAccessTokenCookieHeaders()
        .forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
  }
}
