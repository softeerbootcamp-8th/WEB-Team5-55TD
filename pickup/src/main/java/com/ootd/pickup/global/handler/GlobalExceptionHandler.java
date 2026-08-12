package com.ootd.pickup.global.handler;

import com.ootd.pickup.global.exception.ClientExceptionCode;
import com.ootd.pickup.global.exception.ExceptionResponseFactory;
import com.ootd.pickup.global.exception.JacksonFieldPathResolver;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import com.ootd.pickup.global.slack.ErrorRequestContext;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * {@link ResponseEntityExceptionHandler}를 상속해 잘못된 JSON body, 파라미터 타입 불일치, 필수 파라미터 누락, 존재하지 않는 경로,
 * 지원하지 않는 HTTP 메서드 같은 스프링 프레임워크 예외까지 알맞은 4xx로 응답한다.
 *
 * <p>{@link ResponseEntityExceptionHandler#handleException}이 이 예외들을 이미 {@code @ExceptionHandler}로
 * 선언해 두고 있어, 상속만으로 각 예외 타입이 {@link #handleRuntimeException} catch-all보다 우선
 * 매칭된다({@code @ExceptionHandler} 해석은 더 구체적인 타입을 우선한다). {@link #handleExceptionInternal}만 재정의해 응답
 * 본문을 프로젝트 공통 포맷({@link ExceptionResponse})으로 맞춘다.
 *
 * <p>4xx는 클라이언트 잘못이므로 {@link #handleRuntimeException}과 달리 Slack 알림을 보내지 않는다. 온콜에게는 정말 원인을 찾아야 하는
 * 500만 알린다.
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private final SlackErrorNotifier slackErrorNotifier;

  @ExceptionHandler(PickUpException.class)
  public ResponseEntity<ExceptionResponse> handlePickUpException(
      PickUpException e, HttpServletRequest request) {
    ExceptionResponse response = ExceptionResponseFactory.from(e, request.getRequestURI());
    return ResponseEntity.status(e.getHttpStatusCode()).body(response);
  }

  /** 첫 번째 필드 오류만이 아니라 검증에 실패한 모든 필드 오류를 메시지에 담는다. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::describeFieldError)
            .collect(Collectors.joining(", "));
    if (message.isBlank()) {
      message = "유효하지 않은 입력입니다.";
    }
    return handleExceptionInternal(e, message, headers, status, request);
  }

  /**
   * {@code @ExceptionHandler(HttpMessageNotReadableException.class)}을 새로 선언하면 {@link
   * ResponseEntityExceptionHandler#handleException}도 같은 타입을 처리해 "Ambiguous @ExceptionHandler" 예외로
   * 기동이 실패한다. 그 대신 이 예외 전용으로 이미 열려 있는 protected 훅을 재정의해, 어느 필드에서 읽기가 실패했는지 {@link
   * JacksonFieldPathResolver}로 짚어내고 {@link #handleExceptionInternal}의 일반 메시지보다 구체적인 안내를 준다.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String invalidField = JacksonFieldPathResolver.resolve(e);
    log.warn("요청 본문을 읽을 수 없음 - path={}, field={}", resolvePath(request), invalidField, e);

    String message =
        invalidField == null
            ? "요청 본문 형식이 올바르지 않습니다. 입력값을 확인해주세요."
            : "요청 필드 '%s' 값이 올바르지 않습니다. 입력값을 확인해주세요.".formatted(invalidField);

    return handleExceptionInternal(e, message, headers, status, request);
  }

  private static String describeFieldError(FieldError fieldError) {
    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
  }

  /**
   * {@link ResponseEntityExceptionHandler}가 프레임워크 예외마다 골라 준 {@code statusCode}를 그대로 써서 공통 응답 포맷으로
   * 감싼다. {@code body}가 {@link #handleMethodArgumentNotValid}가 만든 문자열이면 그 메시지를 쓰고, 그 외 프레임워크 예외는 종류별
   * 안내 메시지를 새로 만든다.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    String path = resolvePath(request);
    String clientExceptionCode =
        ex instanceof MethodArgumentNotValidException
            ? ClientExceptionCode.ILLEGAL_ARGUMENT.name()
            : clientExceptionCodeFor(statusCode).name();
    String message =
        body instanceof String stringBody ? stringBody : defaultMessageFor(ex, statusCode);

    ExceptionResponse response =
        new ExceptionResponse(
            statusCode.value(),
            clientExceptionCode,
            message,
            path,
            ZonedDateTime.now(ZoneOffset.UTC));
    return ResponseEntity.status(statusCode).headers(headers).body(response);
  }

  private ClientExceptionCode clientExceptionCodeFor(HttpStatusCode statusCode) {
    if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
      return ClientExceptionCode.RESOURCE_NOT_FOUND;
    }
    if (statusCode.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
      return ClientExceptionCode.METHOD_NOT_ALLOWED;
    }
    if (statusCode.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
      return ClientExceptionCode.UNSUPPORTED_MEDIA_TYPE;
    }
    return ClientExceptionCode.BAD_REQUEST;
  }

  private String defaultMessageFor(Exception ex, HttpStatusCode statusCode) {
    if (ex instanceof HttpRequestMethodNotSupportedException e) {
      return "지원하지 않는 HTTP 메서드입니다: " + e.getMethod();
    }
    if (ex instanceof HttpMediaTypeNotSupportedException e) {
      return "지원하지 않는 Content-Type입니다: " + e.getContentType();
    }
    if (ex instanceof MissingServletRequestParameterException e) {
      return "필수 파라미터가 누락되었습니다: " + e.getParameterName();
    }
    if (ex instanceof MethodArgumentTypeMismatchException e) {
      return "파라미터 타입이 올바르지 않습니다: " + e.getName();
    }
    if (ex instanceof HttpMessageNotReadableException) {
      return "요청 본문을 읽을 수 없습니다.";
    }
    if (ex instanceof NoResourceFoundException) {
      return "요청한 리소스를 찾을 수 없습니다.";
    }
    return statusCode.isSameCodeAs(HttpStatus.NOT_FOUND) ? "요청한 리소스를 찾을 수 없습니다." : "잘못된 요청입니다.";
  }

  private String resolvePath(WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      return servletWebRequest.getRequest().getRequestURI();
    }
    return request.getDescription(false);
  }

  /** 여기까지 온 예외는 프레임워크가 이미 분류한 4xx가 아닌, 원인을 파악해야 하는 예상치 못한 예외다. */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ExceptionResponse> handleRuntimeException(
      RuntimeException runtimeException, HttpServletRequest request) {
    log.error(
        "Unexpected Runtime Exception Occurred - path={}",
        request.getRequestURI(),
        runtimeException);

    // Slack 알림은 온콜 담당자가 바로 읽을 수 있어야 하므로 여기만 예외적으로 KST를 쓴다.
    ErrorRequestContext context =
        ErrorRequestContext.from(request, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    slackErrorNotifier.notifyError(runtimeException, context);

    ExceptionResponse response =
        ExceptionResponseFactory.internalServerError(request.getRequestURI());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}
