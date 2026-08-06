package com.ootd.pickup.auction.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.member.domain.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionEndedNotificationEventTest {

  @Test
  void 낙찰된_경매로부터_생성하면_낙찰_입찰_정보가_중첩되어_옮겨진다() {
    // given
    Auction auction = createAuction(1L, AuctionStatus.WON);
    auction.updateWinningBid(10L, 10_500L);
    Member winner = createMember(2L, "닉네임");
    Bid winningBid = createBid(auction, winner, 10L, 10_500L);

    // when
    AuctionEndedNotificationEvent event =
        AuctionEndedNotificationEvent.fromEntity(auction, winningBid);

    // then
    assertThat(event.auctionId()).isEqualTo(auction.getAuctionId());
    assertThat(event.winningPrice()).isEqualTo(10_500L);
    assertThat(event.auctionStatus()).isEqualTo(AuctionStatus.WON);
    assertThat(event.winningBid().bidId()).isEqualTo(10L);
    assertThat(event.winningBid().memberId()).isEqualTo(2L);
    assertThat(event.winningBid().memberNickname()).isEqualTo("닉네임");
  }

  @Test
  void 유찰된_경매로부터_생성하면_낙찰_입찰_정보가_없다() {
    // given
    Auction auction = createAuction(1L, AuctionStatus.PASSED);

    // when
    AuctionEndedNotificationEvent event = AuctionEndedNotificationEvent.fromEntity(auction, null);

    // then
    assertThat(event.winningBid()).isNull();
  }

  @Test
  void 경매_애그리거트와_이벤트타입이_고정값으로_반환된다() {
    // given
    Auction auction = createAuction(1L, AuctionStatus.PASSED);

    // when
    AuctionEndedNotificationEvent event = AuctionEndedNotificationEvent.fromEntity(auction, null);

    // then
    assertThat(event.aggregateType()).isEqualTo(AggregateType.AUCTION);
    assertThat(event.aggregateId()).isEqualTo(auction.getAuctionId());
    assertThat(event.eventType()).isEqualTo(EventType.AUCTION_ENDED);
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

  private Member createMember(Long memberId, String nickname) {
    Member member = Member.create("loginId" + memberId, "password", nickname);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Bid createBid(Auction auction, Member member, Long bidId, Long bidPrice) {
    Bid bid = Bid.create(auction, member, bidPrice);
    ReflectionTestUtils.setField(bid, "bidId", bidId);
    return bid;
  }
}
