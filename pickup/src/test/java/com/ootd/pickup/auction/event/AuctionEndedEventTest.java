package com.ootd.pickup.auction.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.event.AggregateType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionEndedEventTest {

  @Test
  void 낙찰된_경매로부터_생성하면_낙찰_입찰과_낙찰가가_함께_옮겨진다() {
    // given
    Auction auction = createAuction(1L, AuctionStatus.WON);
    auction.updateWinningBid(10L, 10_500L);

    // when
    AuctionEndedEvent event = AuctionEndedEvent.fromEntity(auction);

    // then
    assertThat(event.auctionId()).isEqualTo(auction.getAuctionId());
    assertThat(event.winningBidId()).isEqualTo(10L);
    assertThat(event.winningPrice()).isEqualTo(10_500L);
    assertThat(event.auctionStatus()).isEqualTo(AuctionStatus.WON);
  }

  @Test
  void 경매_애그리거트와_이벤트타입이_고정값으로_반환된다() {
    // given
    Auction auction = createAuction(1L, AuctionStatus.PASSED);

    // when
    AuctionEndedEvent event = AuctionEndedEvent.fromEntity(auction);

    // then
    assertThat(event.aggregateType()).isEqualTo(AggregateType.AUCTION);
    assertThat(event.aggregateId()).isEqualTo(auction.getAuctionId());
    assertThat(event.eventType()).isEqualTo("AUCTION_ENDED");
  }

  private Auction createAuction(Long auctionId, AuctionStatus status) {
    Consignment consignment =
        Consignment.builder().status(ConsignmentStatus.AUCTION_SCHEDULED).build();
    ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now())
            .auctionStatus(status)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }
}
