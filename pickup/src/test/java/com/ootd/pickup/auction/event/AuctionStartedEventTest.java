package com.ootd.pickup.auction.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionStartedEventTest {

  @Test
  void 경매로부터_생성하면_경매_필드가_그대로_옮겨진다() {
    // given
    Auction auction = createAuction(1L, AuctionStatus.ONGOING);

    // when
    AuctionStartedEvent event = AuctionStartedEvent.fromEntity(auction);

    // then
    assertThat(event.auctionId()).isEqualTo(auction.getAuctionId());
    assertThat(event.consignmentId()).isEqualTo(auction.getConsignment().getConsignmentId());
    assertThat(event.startingPrice()).isEqualTo(auction.getStartingPrice());
    assertThat(event.reservePrice()).isEqualTo(auction.getReservePrice());
    assertThat(event.winningBidId()).isEqualTo(auction.getWinningBidId());
    assertThat(event.winningPrice()).isEqualTo(auction.getWinningPrice());
    assertThat(event.auctionStatus()).isEqualTo(auction.getAuctionStatus());
    assertThat(event.startedAt()).isEqualTo(auction.getStartedAt());
    assertThat(event.endedAt()).isEqualTo(auction.getEndedAt());
    assertThat(event.createdAt()).isEqualTo(auction.getCreatedAt());
    assertThat(event.eventId()).isNotBlank();
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void 경매_애그리거트와_이벤트타입이_고정값으로_반환된다() {
    // given
    Auction auction = createAuction(1L, AuctionStatus.ONGOING);

    // when
    AuctionStartedEvent event = AuctionStartedEvent.fromEntity(auction);

    // then
    assertThat(event.aggregateType()).isEqualTo(AggregateType.AUCTION);
    assertThat(event.aggregateId()).isEqualTo(auction.getAuctionId());
    assertThat(event.eventType()).isEqualTo(EventType.AUCTION_STARTED);
  }

  private Auction createAuction(Long auctionId, AuctionStatus status) {
    Consignment consignment =
        Consignment.builder().status(ConsignmentStatus.AUCTION_SCHEDULED).build();
    ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusMinutes(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(status)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }
}
