package com.ootd.pickup.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionCode {
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      ClientExceptionCode.INTERNAL_SERVER_ERROR,
      "예상치 못한 서버에러가 발생했습니다."),
  ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, ClientExceptionCode.ILLEGAL_ARGUMENT, "필수 파라미터 누락"),
  PIKACHU_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.PIKACHU_NOT_FOUND, "피카츄를 찾을 수 없습니다."),
  CARD_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.CARD_NOT_FOUND, "카드를 찾을 수 없습니다."),
  INVALID_GRADE(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_GRADE, "유효하지 않은 카드 등급입니다."),
  INVALID_CERTIFICATION_BODY(
      HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_CERTIFICATION_BODY, "유효하지 않은 감정 기관입니다."),
  CONSIGNMENT_NOT_FOUND(
      HttpStatus.NOT_FOUND, ClientExceptionCode.CONSIGNMENT_NOT_FOUND, "상품을 찾을 수 없습니다."),
  CERTIFICATE_NOT_FOUND(
      HttpStatus.NOT_FOUND, ClientExceptionCode.CERTIFICATE_NOT_FOUND, "인증서를 찾을 수 없습니다."),
  CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS(
      HttpStatus.CONFLICT,
      ClientExceptionCode.CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS,
      "이미 등록된 인증서 일련번호입니다."),
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.MEMBER_NOT_FOUND, "회원을 찾을 수 없습니다."),
  CONSIGNMENT_MODIFY_OWNER_MISMATCH(
      HttpStatus.FORBIDDEN,
      ClientExceptionCode.CONSIGNMENT_MODIFY_OWNER_MISMATCH,
      "본인이 등록한 상품만 수정할 수 있습니다."),
  CONSIGNMENT_NOT_MODIFIABLE(
      HttpStatus.CONFLICT,
      ClientExceptionCode.CONSIGNMENT_NOT_MODIFIABLE,
      "경매 신청 이후에는 상품 정보를 수정할 수 없습니다."),
  MEMBER_LOGIN_ID_ALREADY_EXISTS(
      HttpStatus.CONFLICT, ClientExceptionCode.MEMBER_LOGIN_ID_ALREADY_EXISTS, "이미 사용 중인 아이디입니다."),
  MEMBER_NICKNAME_ALREADY_EXISTS(
      HttpStatus.CONFLICT, ClientExceptionCode.MEMBER_NICKNAME_ALREADY_EXISTS, "이미 사용 중인 닉네임입니다."),
  INVALID_ACCESS_TOKEN(
      HttpStatus.UNAUTHORIZED, ClientExceptionCode.INVALID_ACCESS_TOKEN, "유효하지 않은 액세스 토큰입니다."),
  AUTHENTICATION_REQUIRED(
      HttpStatus.UNAUTHORIZED, ClientExceptionCode.AUTHENTICATION_REQUIRED, "인증이 필요합니다."),
  INVALID_PASSWORD(
      HttpStatus.UNAUTHORIZED, ClientExceptionCode.INVALID_PASSWORD, "비밀번호가 일치하지 않습니다."),
  INVALID_REFRESH_TOKEN(
      HttpStatus.UNAUTHORIZED, ClientExceptionCode.INVALID_REFRESH_TOKEN, "유효하지 않은 리프레시 토큰입니다."),
  CONSIGNMENT_NOT_REGISTERABLE(
      HttpStatus.CONFLICT,
      ClientExceptionCode.CONSIGNMENT_NOT_REGISTERABLE,
      "이미 경매 진행/예정 중이거나 신청할 수 없는 상태입니다."),
  CONSIGNMENT_AUCTION_OWNER_MISMATCH(
      HttpStatus.FORBIDDEN,
      ClientExceptionCode.CONSIGNMENT_AUCTION_OWNER_MISMATCH,
      "본인이 소유한 상품만 경매 신청할 수 있습니다."),
  INVALID_AUCTION_STATUS(
      HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_AUCTION_STATUS, "유효하지 않은 경매 상태입니다."),
  INVALID_AUCTION_SORT(
      HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_AUCTION_SORT, "유효하지 않은 정렬 기준입니다."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, ClientExceptionCode.INVALID_CURSOR, "유효하지 않은 커서 값입니다."),
  AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, ClientExceptionCode.AUCTION_NOT_FOUND, "경매를 찾을 수 없습니다."),
  AUCTION_NOT_STARTED(
      HttpStatus.CONFLICT, ClientExceptionCode.AUCTION_NOT_STARTED, "아직 시작되지 않은 경매입니다."),
  AUCTION_ENDED(HttpStatus.CONFLICT, ClientExceptionCode.AUCTION_ENDED, "이미 종료된 경매입니다."),
  AUCTION_SELLER_BID_FORBIDDEN(
      HttpStatus.FORBIDDEN,
      ClientExceptionCode.AUCTION_SELLER_BID_FORBIDDEN,
      "판매자는 본인의 경매에 입찰할 수 없습니다."),
  OUTBID_EXISTS(HttpStatus.CONFLICT, ClientExceptionCode.OUTBID_EXISTS, "이미 더 높은 입찰이 존재합니다."),
  BELOW_MIN_INCREMENT(
      HttpStatus.CONFLICT,
      ClientExceptionCode.BELOW_MIN_INCREMENT,
      "현재가에서 최소 입찰 단위 이상 높게 입찰해야 합니다.");

  private final HttpStatus httpStatus;
  private final ClientExceptionCode clientExceptionCode;
  private final String message;

  ExceptionCode(HttpStatus httpStatus, ClientExceptionCode clientExceptionCode, String message) {
    this.httpStatus = httpStatus;
    this.clientExceptionCode = clientExceptionCode;
    this.message = message;
  }
}
