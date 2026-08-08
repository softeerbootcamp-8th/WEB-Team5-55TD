package com.ootd.pickup.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionTest {

  @Test
  void 최고입찰을_갱신하면_낙찰입찰ID와_낙찰가가_함께_바뀐다() {
    // given
    Auction auction =
        Auction.builder()
            .consignment(null)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build();

    // when
    auction.updateWinningBid(10L, 10_500L);

    // then
    assertThat(auction.getWinningBidId()).isEqualTo(10L);
    assertThat(auction.getWinningPrice()).isEqualTo(10_500L);
  }

  @Test
  void 더_높은_입찰로_갱신하면_이전_낙찰입찰_정보를_덮어쓴다() {
    // given
    Auction auction =
        Auction.builder()
            .consignment(null)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build();
    auction.updateWinningBid(10L, 10_500L);

    // when
    auction.updateWinningBid(11L, 11_000L);

    // then
    assertThat(auction.getWinningBidId()).isEqualTo(11L);
    assertThat(auction.getWinningPrice()).isEqualTo(11_000L);
  }

  @Test
  void 종료_5분_이내_입찰이면_입찰시각부터_5분으로_종료시각을_연장한다() {
    LocalDateTime bidAt = LocalDateTime.of(2026, 8, 8, 21, 58);
    Auction auction = ongoingAuction(bidAt.plusMinutes(2));

    boolean extended = auction.extendEndAtForSoftClose(bidAt);

    assertThat(extended).isTrue();
    assertThat(auction.getEndedAt()).isEqualTo(bidAt.plusMinutes(5));
  }

  @Test
  void 종료까지_5분보다_많이_남은_입찰은_종료시각을_바꾸지_않는다() {
    LocalDateTime bidAt = LocalDateTime.of(2026, 8, 8, 21, 50);
    LocalDateTime endedAt = bidAt.plusMinutes(6);
    Auction auction = ongoingAuction(endedAt);

    boolean extended = auction.extendEndAtForSoftClose(bidAt);

    assertThat(extended).isFalse();
    assertThat(auction.getEndedAt()).isEqualTo(endedAt);
  }

  private Auction ongoingAuction(LocalDateTime endedAt) {
    return Auction.builder()
        .consignment(null)
        .startedAt(endedAt.minusDays(7))
        .endedAt(endedAt)
        .auctionStatus(AuctionStatus.ONGOING)
        .startingPrice(10_000L)
        .reservePrice(15_000L)
        .bidIncrement(500L)
        .build();
  }
}
