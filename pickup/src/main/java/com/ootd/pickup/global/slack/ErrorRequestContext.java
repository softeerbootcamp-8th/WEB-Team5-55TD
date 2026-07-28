package com.ootd.pickup.global.slack;

import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletRequest;

public record ErrorRequestContext(
    String method,
    String uri,
    String queryString,
    String clientIp,
    LocalDateTime occurredAt
) {

    public static ErrorRequestContext from(HttpServletRequest request, LocalDateTime occurredAt) {
        return new ErrorRequestContext(
            request.getMethod(),
            request.getRequestURI(),
            request.getQueryString(),
            request.getRemoteAddr(),
            occurredAt
        );
    }
}
