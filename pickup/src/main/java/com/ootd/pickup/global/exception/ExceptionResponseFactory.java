package com.ootd.pickup.global.exception;

import java.time.ZonedDateTime;

import org.springframework.http.HttpStatus;

import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;

public final class ExceptionResponseFactory {
    private ExceptionResponseFactory() {
    }

    public static ExceptionResponse from(ExceptionCode exceptionCode, String path) {
        return new ExceptionResponse(
            exceptionCode.getHttpStatus().value(),
            exceptionCode.getClientExceptionCode().name(),
            exceptionCode.getMessage(),
            path,
            ZonedDateTime.now()
        );
    }

    public static ExceptionResponse from(PickUpException exception, String path) {
        return new ExceptionResponse(
            exception.getHttpStatusCode().value(),
            exception.getExceptionCodeName(),
            exception.getMessage(),
            path,
            ZonedDateTime.now()
        );
    }

    public static ExceptionResponse badRequest(String message, String path) {
        return new ExceptionResponse(
            HttpStatus.BAD_REQUEST.value(),
            ExceptionCode.ILLEGAL_ARGUMENT.name(),
            message,
            path,
            ZonedDateTime.now()
        );
    }

    public static ExceptionResponse internalServerError(String path) {
        return from(ExceptionCode.INTERNAL_SERVER_ERROR, path);
    }
}
