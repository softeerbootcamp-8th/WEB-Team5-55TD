package com.ootd.pickup.global.handler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.global.slack.ErrorRequestContext;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import com.ootd.pickup.health.controller.HealthCheckController;
import com.ootd.pickup.health.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
}
