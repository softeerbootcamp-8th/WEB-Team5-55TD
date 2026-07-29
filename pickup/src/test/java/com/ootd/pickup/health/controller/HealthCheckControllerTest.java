package com.ootd.pickup.health.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.global.slack.SlackErrorNotifier;
import com.ootd.pickup.health.dto.response.HealthCheckResponse;
import com.ootd.pickup.health.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthCheckController.class)
class HealthCheckControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HealthCheckService healthCheckService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 헬스체크_API_호출_시_서버_상태를_반환한다() throws Exception {
    given(healthCheckService.getHealthCheckStatus()).willReturn(new HealthCheckResponse("OK"));

    mockMvc
        .perform(get("/healthcheck"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"));
  }
}
