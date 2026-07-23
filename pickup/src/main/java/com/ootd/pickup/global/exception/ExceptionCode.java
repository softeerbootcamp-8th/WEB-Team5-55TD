package com.ootd.pickup.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionCode {
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ClientExceptionCode.INTERNAL_SERVER_ERROR, "예상치 못한 서버에러가 발생했습니다."),
    ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, ClientExceptionCode.ILLEGAL_ARGUMENT, "필수 파라미터 누락"),
    PIKACHU_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.PIKACHU_NOT_FOUND, "피카츄를 찾을 수 없습니다."),
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.CARD_NOT_FOUND, "카드를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final ClientExceptionCode clientExceptionCode;
    private final String message;

    ExceptionCode(HttpStatus httpStatus, ClientExceptionCode clientExceptionCode, String message) {
        this.httpStatus = httpStatus;
        this.clientExceptionCode = clientExceptionCode;
        this.message = message;
    }
}
