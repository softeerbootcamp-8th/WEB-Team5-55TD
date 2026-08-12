package com.ootd.pickup.auction.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.GetSalesHistoryRequest;
import com.ootd.pickup.auction.dto.response.SaleHistoryItemResponse;
import com.ootd.pickup.auction.service.SalesService;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SalesController.class)
class SalesControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SalesService salesService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 인증된_회원이_판매_내역을_조회하면_200과_회원ID가_서비스에_전달된다() throws Exception {
    // given
    given(salesService.getSalesHistory(eq(1L), any(GetSalesHistoryRequest.class)))
        .willReturn(CursorPageResponse.from(List.of(createItem()), false, null));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/sales")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.items[0].auctionId").value(1L))
        .andExpect(jsonPath("$.items[0].resultType").value("WON"));

    then(salesService).should().getSalesHistory(eq(1L), any(GetSalesHistoryRequest.class));
  }

  @Test
  void 인증없이_판매_내역을_조회하면_401을_반환한다() throws Exception {
    // when & then
    mockMvc.perform(get("/sellers/me/sales")).andExpect(status().isUnauthorized());

    then(salesService).shouldHaveNoInteractions();
  }

  @Test
  void status가_잘못되면_400을_반환한다() throws Exception {
    // given
    given(salesService.getSalesHistory(eq(1L), any(GetSalesHistoryRequest.class)))
        .willThrow(new PickUpException(INVALID_AUCTION_STATUS));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/sales")
                .param("status", "ONGOING")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_AUCTION_STATUS.getMessage()));
  }

  @Test
  void 커서가_잘못되면_400을_반환한다() throws Exception {
    // given
    given(salesService.getSalesHistory(eq(1L), any(GetSalesHistoryRequest.class)))
        .willThrow(new PickUpException(INVALID_CURSOR));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/sales")
                .param("cursor", "invalid")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_CURSOR.getMessage()));
  }

  private SaleHistoryItemResponse createItem() {
    return new SaleHistoryItemResponse(
        1L,
        new GetCardDetailResponse(10L, "리자몽", "Base Set", "4/102", "일본어", "레어 홀로", "https://img"),
        "PSA 10",
        12000L,
        AuctionStatus.WON);
  }
}
