package com.ootd.pickup.auction.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.request.SearchAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
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
import org.mockito.ArgumentCaptor;
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

  @Test
  void 인증된_사용자가_목록을_조회하면_200과_회원ID가_서비스에_전달된다() throws Exception {
    // given
    given(auctionService.searchAuctions(eq(1L), any(SearchAuctionsRequest.class)))
        .willReturn(CursorPageResponse.from(List.of(createListItem()), false, null));

    // when & then
    mockMvc
        .perform(
            get("/auctions")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.items[0].auctionId").value(1L));

    then(auctionService).should().searchAuctions(eq(1L), any(SearchAuctionsRequest.class));
  }

  @Test
  void 비로그인_사용자가_목록을_조회해도_200을_반환하고_회원ID는_null이다() throws Exception {
    // given
    given(auctionService.searchAuctions(isNull(), any(SearchAuctionsRequest.class)))
        .willReturn(CursorPageResponse.from(List.of(), false, null));

    // when & then
    mockMvc.perform(get("/auctions")).andExpect(status().isOk());

    then(auctionService).should().searchAuctions(isNull(), any(SearchAuctionsRequest.class));
  }

  @Test
  void status가_여러개면_리스트로_바인딩된다() throws Exception {
    // given
    given(auctionService.searchAuctions(isNull(), any(SearchAuctionsRequest.class)))
        .willReturn(CursorPageResponse.from(List.of(), false, null));

    // when
    mockMvc
        .perform(get("/auctions").param("status", "ONGOING", "SCHEDULED"))
        .andExpect(status().isOk());

    // then
    ArgumentCaptor<SearchAuctionsRequest> captor =
        ArgumentCaptor.forClass(SearchAuctionsRequest.class);
    then(auctionService).should().searchAuctions(isNull(), captor.capture());
    assertThat(captor.getValue().status()).containsExactly("ONGOING", "SCHEDULED");
  }

  @Test
  void limit이_있으면_hasNext가_false로_반환된다() throws Exception {
    // given
    given(auctionService.searchAuctions(isNull(), any(SearchAuctionsRequest.class)))
        .willReturn(CursorPageResponse.from(List.of(createListItem()), false, null));

    // when & then
    mockMvc
        .perform(get("/auctions").param("limit", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.cursor").doesNotExist());
  }

  @Test
  void 잘못된_정렬값이면_400을_반환한다() throws Exception {
    // given
    given(auctionService.searchAuctions(isNull(), any(SearchAuctionsRequest.class)))
        .willThrow(new PickUpException(INVALID_AUCTION_SORT));

    // when & then
    mockMvc
        .perform(get("/auctions").param("sort", "INVALID"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_AUCTION_SORT.getMessage()));
  }

  @Test
  void 잘못된_상태값이면_400을_반환한다() throws Exception {
    // given
    given(auctionService.searchAuctions(isNull(), any(SearchAuctionsRequest.class)))
        .willThrow(new PickUpException(INVALID_AUCTION_STATUS));

    // when & then
    mockMvc
        .perform(get("/auctions").param("status", "INVALID"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(INVALID_AUCTION_STATUS.getMessage()));
  }

  private AuctionListItemResponse createListItem() {
    return new AuctionListItemResponse(
        1L,
        100L,
        new GetCardDetailResponse(10L, "리자몽", "Base Set", "4/102", "일본어", "MINT", "https://img"),
        "PSA 10",
        AuctionStatus.SCHEDULED,
        10000L,
        null,
        LocalDateTime.now().plusDays(1),
        null,
        null,
        0L,
        false,
        "https://thumb");
  }

  private CreateAuctionRequest createRequest(LocalDateTime scheduledStartAt) {
    return new CreateAuctionRequest(100L, 10000L, 15000L, scheduledStartAt);
  }
}
