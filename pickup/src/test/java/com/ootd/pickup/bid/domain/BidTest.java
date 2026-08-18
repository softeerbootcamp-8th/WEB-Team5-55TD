package com.ootd.pickup.bid.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BidTest {

  @Test
  void 최고_입찰이고_경매가_진행중이면_HIGHEST다() {
    // given
    Auction auction = createAuction(AuctionStatus.ONGOING);
    Bid bid = createBid(auction, 1L);
    auction.updateWinningBid(bid.getBidId(), bid.getBidPrice());

    // when & then
    assertThat(bid.getBidStatus()).isEqualTo(BidStatus.HIGHEST);
  }

  @Test
  void 최고_입찰이_아니면_OUTBID다() {
    // given
    Auction auction = createAuction(AuctionStatus.ONGOING);
    Bid bid = createBid(auction, 1L);
    auction.updateWinningBid(999L, 20_000L);

    // when & then
    assertThat(bid.getBidStatus()).isEqualTo(BidStatus.OUTBID);
  }

  @Test
  void 최고_입찰이고_경매가_낙찰되면_WON이다() {
    // given
    Auction auction = createAuction(AuctionStatus.WON);
    Bid bid = createBid(auction, 1L);
    auction.updateWinningBid(bid.getBidId(), bid.getBidPrice());

    // when & then
    assertThat(bid.getBidStatus()).isEqualTo(BidStatus.WON);
  }

  private Auction createAuction(AuctionStatus auctionStatus) {
    return Auction.builder()
        .title("테스트 제목")
        .description("테스트 설명")
        .startedAt(LocalDateTime.now().minusHours(1))
        .endedAt(LocalDateTime.now().plusHours(1))
        .auctionStatus(auctionStatus)
        .startingPrice(10_000L)
        .reservePrice(15_000L)
        .bidIncrement(500L)
        .build();
  }

  private Bid createBid(Auction auction, Long bidId) {
    Bid bid = Bid.create(auction, null, 10_500L);
    ReflectionTestUtils.setField(bid, "bidId", bidId);
    return bid;
  }
}
