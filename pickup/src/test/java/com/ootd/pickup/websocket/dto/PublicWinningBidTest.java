package com.ootd.pickup.websocket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.event.WinningBidSnapshot;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.websocket.dto.PublicWinningBid;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PublicWinningBidTest {

  @Test
  void 낙찰_정보를_변환하면_닉네임을_그대로_반환한다() {
    // given
    WinningBidSnapshot snapshot = snapshot("귀염사쿠");

    // when
    PublicWinningBid winningBid = PublicWinningBid.fromEvent(snapshot);

    // then
    assertThat(winningBid.nickname()).isEqualTo("귀염사쿠");
  }

  private WinningBidSnapshot snapshot(String nickname) {
    return new WinningBidSnapshot(
        1L, 2L, nickname, 10_000L, BidStatus.HIGHEST, LocalDateTime.of(2026, 8, 5, 10, 30));
  }
}
