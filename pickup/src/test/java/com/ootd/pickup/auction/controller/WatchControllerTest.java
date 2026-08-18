package com.ootd.pickup.auction.controller;

import static com.ootd.pickup.global.exception.ExceptionCode.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.dto.response.WatchResponse;
import com.ootd.pickup.auction.service.WatchService;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WatchController.class)
class WatchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private WatchService watchService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 인증된_회원이_관심을_등록하면_201과_관심정보를_반환한다() throws Exception {
    // given
    LocalDateTime createdAt = LocalDateTime.of(2026, 7, 31, 12, 0);
    given(watchService.registerWatch(1L, 100L))
        .willReturn(new WatchResponse(10L, 1L, 100L, createdAt));

    // when & then
    mockMvc
        .perform(
            post("/auctions/{auctionId}/watch", 100L)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.watchId").value(10L))
        .andExpect(jsonPath("$.memberId").value(1L))
        .andExpect(jsonPath("$.auctionId").value(100L))
        .andExpect(jsonPath("$.createdAt").value("2026-07-31T12:00:00"));
  }

  @Test
  void 인증된_회원이_관심을_해제하면_204를_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(
            delete("/auctions/{auctionId}/watch", 100L)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    then(watchService).should().deleteWatch(1L, 100L);
  }

  @Test
  void 인증없이_관심을_등록하면_401을_반환한다() throws Exception {
    // when & then
    mockMvc.perform(post("/auctions/{auctionId}/watch", 100L)).andExpect(status().isUnauthorized());

    then(watchService).shouldHaveNoInteractions();
  }

  @Test
  void 인증없이_관심을_해제하면_401을_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(delete("/auctions/{auctionId}/watch", 100L))
        .andExpect(status().isUnauthorized());

    then(watchService).shouldHaveNoInteractions();
  }

  @Test
  void 존재하지_않는_경매에_관심을_등록하면_404를_반환한다() throws Exception {
    // given
    given(watchService.registerWatch(1L, 999L)).willThrow(new PickUpException(AUCTION_NOT_FOUND));

    // when & then
    mockMvc
        .perform(
            post("/auctions/{auctionId}/watch", 999L)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(AUCTION_NOT_FOUND.getMessage()));
  }

  @Test
  void 이미_관심등록한_경매를_다시_등록하면_409를_반환한다() throws Exception {
    // given
    given(watchService.registerWatch(1L, 100L))
        .willThrow(new PickUpException(WATCH_ALREADY_EXISTS));

    // when & then
    mockMvc
        .perform(
            post("/auctions/{auctionId}/watch", 100L)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(WATCH_ALREADY_EXISTS.getMessage()));
  }

  @Test
  void 판매자가_본인의_경매에_관심을_등록하면_403을_반환한다() throws Exception {
    // given
    given(watchService.registerWatch(1L, 100L))
        .willThrow(new PickUpException(AUCTION_SELLER_WATCH_FORBIDDEN));

    // when & then
    mockMvc
        .perform(
            post("/auctions/{auctionId}/watch", 100L)
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(AUCTION_SELLER_WATCH_FORBIDDEN.getMessage()));
  }
}
