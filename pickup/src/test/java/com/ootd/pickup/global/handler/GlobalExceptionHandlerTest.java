package com.ootd.pickup.global.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import com.ootd.pickup.global.slack.ErrorRequestContext;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import com.ootd.pickup.health.controller.HealthCheckController;
import com.ootd.pickup.health.service.HealthCheckService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

@WebMvcTest(HealthCheckController.class)
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HealthCheckService healthCheckService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 처리되지_않은_런타임_예외가_발생하면_500과_함께_슬랙_알림을_전송한다() throws Exception {
    // given
    given(healthCheckService.getHealthCheckStatus())
        .willThrow(new IllegalStateException("헬스체크 실패"));

    // when
    mockMvc.perform(get("/healthcheck")).andExpect(status().isInternalServerError());

    // then
    then(slackErrorNotifier)
        .should()
        .notifyError(any(RuntimeException.class), any(ErrorRequestContext.class));
  }

  @Test
  void 필드_오류가_없는_검증_예외는_기본_메시지를_사용한다() throws NoSuchMethodException {
    // given
    GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(SlackErrorNotifier.class));
    MethodParameter methodParameter = new MethodParameter(String.class.getMethod("length"), -1);
    BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(methodParameter, bindingResult);
    HttpServletRequest request = mock(HttpServletRequest.class);
    given(request.getRequestURI()).willReturn("/test");

    // when
    ResponseEntity<ExceptionResponse> response =
        handler.handleMethodArgumentNotValidException(exception, request);

    // then
    assertThat(response.getBody().message()).isEqualTo("유효하지 않은 입력입니다.");
  }

  @Test
  void BIGINT_범위를_초과한_숫자_필드로_요청하면_500이_아닌_400을_반환한다() throws Exception {
    // given
    GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(SlackErrorNotifier.class));
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException("Long 범위를 초과한 숫자입니다.", mock(HttpInputMessage.class));
    HttpServletRequest request = mock(HttpServletRequest.class);
    given(request.getRequestURI()).willReturn("/consignments");

    // when
    ResponseEntity<ExceptionResponse> response =
        handler.handleHttpMessageNotReadableException(exception, request);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().message()).isEqualTo("요청 본문 형식이 올바르지 않습니다. 입력값을 확인해주세요.");
  }
}
