package com.ootd.pickup.auction.domain;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionTest {

  @Test
  void 예정_상태의_경매를_취소하면_취소_상태가_된다() {
    // given
    Auction auction = createAuction(AuctionStatus.SCHEDULED);

    // when
    auction.cancel();

    // then
    assertThat(auction.getAuctionStatus()).isEqualTo(AuctionStatus.CANCELLED);
  }

  @Test
  void 진행중_상태의_경매를_취소하면_취소_상태가_된다() {
    // given
    Auction auction = createAuction(AuctionStatus.ONGOING);

    // when
    auction.cancel();

    // then
    assertThat(auction.getAuctionStatus()).isEqualTo(AuctionStatus.CANCELLED);
  }

  @Test
  void 낙찰된_경매를_취소하면_예외가_발생한다() {
    // given
    Auction auction = createAuction(AuctionStatus.WON);

    // when & then
    assertThatThrownBy(auction::cancel).isInstanceOf(PickUpException.class);
  }

  @Test
  void 유찰된_경매를_취소하면_예외가_발생한다() {
    // given
    Auction auction = createAuction(AuctionStatus.PASSED);

    // when & then
    assertThatThrownBy(auction::cancel).isInstanceOf(PickUpException.class);
  }

  @Test
  void 이미_취소된_경매를_다시_취소하면_예외가_발생한다() {
    // given
    Auction auction = createAuction(AuctionStatus.CANCELLED);

    // when & then
    assertThatThrownBy(auction::cancel).isInstanceOf(PickUpException.class);
  }

  @Test
  void 최고입찰을_갱신하면_낙찰입찰ID와_낙찰가가_함께_바뀐다() {
    // given
    Auction auction = createAuction(AuctionStatus.ONGOING);

    // when
    auction.updateWinningBid(10L, 10_500L);

    // then
    assertThat(auction.getWinningBidId()).isEqualTo(10L);
    assertThat(auction.getWinningPrice()).isEqualTo(10_500L);
  }

  @Test
  void 더_높은_입찰로_갱신하면_이전_낙찰입찰_정보를_덮어쓴다() {
    // given
    Auction auction = createAuction(AuctionStatus.ONGOING);
    auction.updateWinningBid(10L, 10_500L);

    // when
    auction.updateWinningBid(11L, 11_000L);

    // then
    assertThat(auction.getWinningBidId()).isEqualTo(11L);
    assertThat(auction.getWinningPrice()).isEqualTo(11_000L);
  }

  private Auction createAuction(AuctionStatus status) {
    return Auction.builder()
        .consignment(null)
        .startedAt(LocalDateTime.now())
        .endedAt(LocalDateTime.now().plusDays(1))
        .auctionStatus(status)
        .startingPrice(1000L)
        .reservePrice(1000L)
        .bidIncrement(50L)
        .build();
  }
}
