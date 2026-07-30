package com.ootd.pickup.auction.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.service.AuctionService;
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

@WebMvcTest(AuctionController.class)
class AuctionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuctionService auctionService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 유효한_요청으로_경매를_신청하면_201과_경매_정보를_반환한다() throws Exception {
    // given
    LocalDateTime scheduledStartAt = LocalDateTime.now().plusDays(1);
    CreateAuctionRequest request = createRequest(scheduledStartAt);
    CreateAuctionResponse response =
        new CreateAuctionResponse(
            1L,
            100L,
            AuctionStatus.SCHEDULED,
            10000L,
            500L,
            scheduledStartAt,
            null,
            null,
            null,
            LocalDateTime.now());
    given(auctionService.registerAuction(eq(1L), any(CreateAuctionRequest.class)))
        .willReturn(response);

    // when & then
    mockMvc
        .perform(
            post("/auctions")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.auctionId").value(1L))
        .andExpect(jsonPath("$.consignmentId").value(100L))
        .andExpect(jsonPath("$.auctionStatus").value("SCHEDULED"))
        .andExpect(jsonPath("$.startingPrice").value(10000L))
        .andExpect(jsonPath("$.bidIncrement").value(500L));
  }

  @Test
  void 인증_정보가_없으면_401을_반환한다() throws Exception {
    // given
    CreateAuctionRequest request = createRequest(LocalDateTime.now().plusDays(1));

    // when & then
    mockMvc
        .perform(
            post("/auctions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());

    then(auctionService).shouldHaveNoInteractions();
  }

  @Test
  void 위탁상품ID가_없으면_400을_반환한다() throws Exception {
    // given
    CreateAuctionRequest request =
        new CreateAuctionRequest(null, 10000L, 15000L, LocalDateTime.now().plusDays(1));

    // when & then
    mockMvc
        .perform(
            post("/auctions")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    then(auctionService).shouldHaveNoInteractions();
  }

  @Test
  void 판매자_본인이_아니면_403을_반환한다() throws Exception {
    // given
    CreateAuctionRequest request = createRequest(LocalDateTime.now().plusDays(1));
    given(auctionService.registerAuction(eq(1L), any(CreateAuctionRequest.class)))
        .willThrow(new PickUpException(CONSIGNMENT_ACCESS_DENIED));

    // when & then
    mockMvc
        .perform(
            post("/auctions")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_ACCESS_DENIED.getMessage()));
  }

  @Test
  void 이미_경매_신청_가능한_상태가_아니면_409를_반환한다() throws Exception {
    // given
    CreateAuctionRequest request = createRequest(LocalDateTime.now().plusDays(1));
    given(auctionService.registerAuction(eq(1L), any(CreateAuctionRequest.class)))
        .willThrow(new PickUpException(CONSIGNMENT_NOT_REGISTERABLE));

    // when & then
    mockMvc
        .perform(
            post("/auctions")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CONSIGNMENT_NOT_REGISTERABLE.getMessage()));
  }

  private CreateAuctionRequest createRequest(LocalDateTime scheduledStartAt) {
    return new CreateAuctionRequest(100L, 10000L, 15000L, scheduledStartAt);
  }
}
