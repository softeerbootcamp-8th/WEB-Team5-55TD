package com.ootd.pickup.bid.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.service.BidService;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(BidController.class)
class BidControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private BidService bidService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 유효한_입찰요청이면_201과_최고입찰_정보를_반환한다() throws Exception {
    // given
    PlaceBidRequest request = new PlaceBidRequest(10_500L);
    PlaceBidResponse response =
        new PlaceBidResponse(
            10L, 1L, 2L, 10_500L, BidStatus.HIGHEST, LocalDateTime.of(2026, 7, 30, 12, 0));
    given(bidService.placeBid(eq(1L), eq(2L), any(PlaceBidRequest.class))).willReturn(response);

    // when & then
    mockMvc
        .perform(
            post("/auctions/1/bids")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(2L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.bidId").value(10L))
        .andExpect(jsonPath("$.auctionId").value(1L))
        .andExpect(jsonPath("$.memberId").value(2L))
        .andExpect(jsonPath("$.bidPrice").value(10_500L))
        .andExpect(jsonPath("$.bidStatus").value("HIGHEST"))
        .andExpect(jsonPath("$.createdAt").value("2026-07-30T12:00:00"));
  }

  @Test
  void 인증정보가_없으면_401을_반환한다() throws Exception {
    // given
    PlaceBidRequest request = new PlaceBidRequest(10_500L);

    // when & then
    mockMvc
        .perform(
            post("/auctions/1/bids")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
    then(bidService).shouldHaveNoInteractions();
  }

  @Test
  void 입찰가가_없으면_400을_반환한다() throws Exception {
    // given
    PlaceBidRequest request = new PlaceBidRequest(null);

    // when & then
    mockMvc
        .perform(
            post("/auctions/1/bids")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(2L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    then(bidService).shouldHaveNoInteractions();
  }

  @Test
  void 입찰가가_양수가_아니면_400을_반환한다() throws Exception {
    // given
    PlaceBidRequest request = new PlaceBidRequest(0L);

    // when & then
    mockMvc
        .perform(
            post("/auctions/1/bids")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(2L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    then(bidService).shouldHaveNoInteractions();
  }

  @Test
  void 판매자가_입찰하면_403과_오류코드를_반환한다() throws Exception {
    // given
    PlaceBidRequest request = new PlaceBidRequest(10_500L);
    given(bidService.placeBid(eq(1L), eq(1L), any(PlaceBidRequest.class)))
        .willThrow(new PickUpException(AUCTION_SELLER_BID_FORBIDDEN));

    // when & then
    mockMvc
        .perform(
            post("/auctions/1/bids")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("AUCTION_SELLER_BID_FORBIDDEN"));
  }

  @Test
  void 최소입찰단위_미만이면_409와_오류코드를_반환한다() throws Exception {
    // given
    PlaceBidRequest request = new PlaceBidRequest(10_499L);
    given(bidService.placeBid(eq(1L), eq(2L), any(PlaceBidRequest.class)))
        .willThrow(new PickUpException(BELOW_MIN_INCREMENT));

    // when & then
    mockMvc
        .perform(
            post("/auctions/1/bids")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(2L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("BELOW_MIN_INCREMENT"));
  }
}
