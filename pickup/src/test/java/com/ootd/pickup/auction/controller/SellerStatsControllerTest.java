package com.ootd.pickup.auction.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.dto.response.SellerStatsResponse;
import com.ootd.pickup.auction.service.SellerStatsService;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerStatsController.class)
class SellerStatsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SellerStatsService sellerStatsService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 인증된_회원이_통계를_조회하면_200과_회원ID가_서비스에_전달된다() throws Exception {
    // given
    given(sellerStatsService.getMyStats(1L)).willReturn(new SellerStatsResponse(12L, 5L, 2L, 38L));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/stats")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registeredConsignments").value(12))
        .andExpect(jsonPath("$.scheduledAuctions").value(5))
        .andExpect(jsonPath("$.ongoingAuctions").value(2))
        .andExpect(jsonPath("$.wonConsignments").value(38));

    then(sellerStatsService).should().getMyStats(1L);
  }

  @Test
  void 인증없이_통계를_조회하면_401을_반환한다() throws Exception {
    // when & then
    mockMvc.perform(get("/sellers/me/stats")).andExpect(status().isUnauthorized());

    then(sellerStatsService).shouldHaveNoInteractions();
  }
}
