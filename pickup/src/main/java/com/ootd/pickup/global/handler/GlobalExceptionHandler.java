package com.ootd.pickup.global.handler;

import com.ootd.pickup.global.exception.ExceptionResponseFactory;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PickUpException.class)
    public ResponseEntity<ExceptionResponse> handleYbcException(PickUpException e, HttpServletRequest request) {
        ExceptionResponse response = ExceptionResponseFactory.from(e, request.getRequestURI());
        return ResponseEntity.status(e.getHttpStatusCode())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                                                   HttpServletRequest request) {
        String errorMessage = "유효하지 않은 입력입니다.";
        FieldError fieldError = e.getFieldError();
        if (fieldError != null) {
            errorMessage = fieldError.getDefaultMessage();
        }

        ExceptionResponse response = ExceptionResponseFactory.badRequest(errorMessage, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ExceptionResponse> handleRuntimeException(RuntimeException runtimeException,
                                                                    HttpServletRequest request) {
        ExceptionResponse response = ExceptionResponseFactory.internalServerError(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}