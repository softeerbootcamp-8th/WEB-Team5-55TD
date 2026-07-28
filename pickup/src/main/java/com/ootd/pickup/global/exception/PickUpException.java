package com.ootd.pickup.global.exception;

import org.springframework.http.HttpStatusCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PickUpException extends RuntimeException {
    private final ExceptionCode exceptionCode;

    @Override
    public String getMessage() {
        return exceptionCode.getMessage();
    }

    public HttpStatusCode getHttpStatusCode() {
        return exceptionCode.getHttpStatus();
    }

    public String getExceptionCodeName() {
        return exceptionCode.getClientExceptionCode().name();
    }
}
