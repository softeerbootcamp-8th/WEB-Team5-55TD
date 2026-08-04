package com.ootd.pickup.global.filter;

import com.ootd.pickup.auth.token.InvalidAccessTokenException;
import com.ootd.pickup.auth.token.InvalidAdminAccessTokenException;
import com.ootd.pickup.global.auth.AdminTokenCookieManager;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class ExceptionHandlingFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper;
  private final TokenCookieManager tokenCookieManager;
  private final AdminTokenCookieManager adminTokenCookieManager;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } catch (InvalidAccessTokenException exception) {
      writeExceptionResponse(
          request,
          response,
          ExceptionCode.INVALID_ACCESS_TOKEN,
          tokenCookieManager.createExpiredAccessTokenCookieHeaders());
    } catch (InvalidAdminAccessTokenException exception) {
      writeExceptionResponse(
          request,
          response,
          ExceptionCode.INVALID_ADMIN_ACCESS_TOKEN,
          adminTokenCookieManager.createExpiredTokenCookieHeaders());
    }
  }

  private void writeExceptionResponse(
      HttpServletRequest request,
      HttpServletResponse response,
      ExceptionCode exceptionCode,
      HttpHeaders expiredCookieHeaders)
      throws IOException {
    ExceptionResponse body = ExceptionResponseFactory.from(exceptionCode, request.getRequestURI());

    expiredCookieHeaders.forEach(
        (name, values) -> values.forEach(value -> response.addHeader(name, value)));
    response.setStatus(exceptionCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
