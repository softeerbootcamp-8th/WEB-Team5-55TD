package com.ootd.pickup.auction.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.GetMyAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.service.AuctionService;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerAuctionsController.class)
class SellerAuctionsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuctionService auctionService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 인증된_회원이_진행중_경매를_조회하면_200과_회원ID가_서비스에_전달된다() throws Exception {
    // given
    given(auctionService.getMyOngoingAuctions(eq(1L), any(GetMyAuctionsRequest.class)))
        .willReturn(CursorPageResponse.from(List.of(createItem()), false, null));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/auctions")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.items[0].auctionId").value(1L))
        .andExpect(jsonPath("$.items[0].auctionStatus").value("ONGOING"));

    then(auctionService).should().getMyOngoingAuctions(eq(1L), any(GetMyAuctionsRequest.class));
  }

  @Test
  void 인증없이_진행중_경매를_조회하면_401을_반환한다() throws Exception {
    // when & then
    mockMvc.perform(get("/sellers/me/auctions")).andExpect(status().isUnauthorized());

    then(auctionService).shouldHaveNoInteractions();
  }

  @Test
  void 커서가_잘못되면_400을_반환한다() throws Exception {
    // given
    given(auctionService.getMyOngoingAuctions(eq(1L), any(GetMyAuctionsRequest.class)))
        .willThrow(new PickUpException(INVALID_CURSOR));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/auctions")
                .param("cursor", "invalid")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_CURSOR.getMessage()));
  }

  private AuctionListItemResponse createItem() {
    return new AuctionListItemResponse(
        1L,
        1L,
        new GetCardDetailResponse(10L, "리자몽", "Base Set", "4/102", "일본어", "MINT", "https://img"),
        "PSA 10",
        AuctionStatus.ONGOING,
        10000L,
        null,
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now().plusHours(1),
        3600L,
        3L,
        false,
        "https://thumb");
  }
}
