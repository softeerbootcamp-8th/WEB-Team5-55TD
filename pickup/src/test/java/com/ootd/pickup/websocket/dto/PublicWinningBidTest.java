package com.ootd.pickup.websocket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.event.WinningBidSnapshot;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.websocket.dto.PublicWinningBid;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PublicWinningBidTest {

  @Test
  void 닉네임의_첫_글자와_마지막_글자만_노출한다() {
    PublicWinningBid winningBid = PublicWinningBid.fromEvent(snapshot("귀염사쿠"));

    assertThat(winningBid.nicknameMasked()).isEqualTo("귀***쿠");
  }

  @Test
  void 닉네임이_null이거나_비어_있으면_전체를_가린다() {
    PublicWinningBid nullNickname = PublicWinningBid.fromEvent(snapshot(null));
    PublicWinningBid blankNickname = PublicWinningBid.fromEvent(snapshot(" "));

    assertThat(nullNickname.nicknameMasked()).isEqualTo("***");
    assertThat(blankNickname.nicknameMasked()).isEqualTo("***");
  }

  @Test
  void 한_글자_닉네임은_전체를_가린다() {
    PublicWinningBid winningBid = PublicWinningBid.fromEvent(snapshot("피"));

    assertThat(winningBid.nicknameMasked()).isEqualTo("***");
  }

  @Test
  void 두_글자_닉네임은_첫_글자와_마지막_글자를_노출한다() {
    PublicWinningBid winningBid = PublicWinningBid.fromEvent(snapshot("피카"));

    assertThat(winningBid.nicknameMasked()).isEqualTo("피***카");
  }

  @Test
  void 이모지가_포함되어도_문자가_깨지지_않는다() {
    PublicWinningBid winningBid = PublicWinningBid.fromEvent(snapshot("😀피카츄😺"));

    assertThat(winningBid.nicknameMasked()).isEqualTo("😀***😺");
  }

  private WinningBidSnapshot snapshot(String nickname) {
    return new WinningBidSnapshot(
        1L, 2L, nickname, 10_000L, BidStatus.HIGHEST, LocalDateTime.of(2026, 8, 5, 10, 30));
  }
}
