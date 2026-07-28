package com.ootd.pickup.global.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ootd.pickup.auth.token.InvalidAccessTokenException;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.ExceptionResponseFactory;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class ExceptionHandlingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (InvalidAccessTokenException exception) {
            writeInvalidAccessTokenResponse(request, response);
        }
    }

    private void writeInvalidAccessTokenResponse(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        ExceptionCode exceptionCode = ExceptionCode.INVALID_ACCESS_TOKEN;
        ExceptionResponse body = ExceptionResponseFactory.from(
            exceptionCode,
            request.getRequestURI()
        );

        response.setStatus(exceptionCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
