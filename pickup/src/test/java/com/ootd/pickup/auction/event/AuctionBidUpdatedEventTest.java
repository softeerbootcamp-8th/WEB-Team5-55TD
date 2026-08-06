package com.ootd.pickup.auction.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.member.domain.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionBidUpdatedEventTest {

  @Test
  void 경매와_최고입찰로부터_입찰_알림_이벤트를_생성한다() {
    Auction auction = createAuction(1L);
    auction.updateWinningBid(10L, 10_500L);
    Member bidder = createMember(2L);
    Bid bid = createBid(auction, bidder, 10L, 10_500L);

    AuctionBidUpdatedEvent event = AuctionBidUpdatedEvent.fromEntity(auction, bid);

    assertThat(event.auctionId()).isEqualTo(1L);
    assertThat(event.winningPrice()).isEqualTo(10_500L);
    assertThat(event.winningBid().bidId()).isEqualTo(10L);
    assertThat(event.winningBid().memberId()).isEqualTo(2L);
    assertThat(event.winningBid().bidPrice()).isEqualTo(10_500L);
    assertThat(event.aggregateType()).isEqualTo(AggregateType.AUCTION);
    assertThat(event.aggregateId()).isEqualTo(1L);
    assertThat(event.eventType()).isEqualTo("AUCTION_BID_UPDATED");
  }

  private Auction createAuction(Long auctionId) {
    Consignment consignment =
        Consignment.builder().status(ConsignmentStatus.AUCTION_SCHEDULED).build();
    ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusMinutes(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("loginId" + memberId, "password", "닉네임" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Bid createBid(Auction auction, Member member, Long bidId, Long bidPrice) {
    Bid bid = Bid.create(auction, member, bidPrice);
    ReflectionTestUtils.setField(bid, "bidId", bidId);
    return bid;
  }
}
