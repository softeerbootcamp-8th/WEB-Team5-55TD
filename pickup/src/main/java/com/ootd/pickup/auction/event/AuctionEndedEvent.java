package com.ootd.pickup.auction.event;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 경매 종료(ONGOING → WON/PASSED) 사건.
 *
 * <p>후속 처리(정산 등)가 정확히 한 번만 실행돼야 하므로 {@link MessageQueueEvent}로 분류한다. Outbox에 먼저 저장되고 별도 Relay가 SQS
 * FIFO 큐로 옮긴다.
 *
 * <p>정산 컨슈머는 다른 프로세스에서 트랜잭션 밖에 실행되므로 낙찰자·판매자를 다시 조회할 수 없다. 그래서 마감 스케줄러가 이미 로드해 둔 {@code
 * winningBid}/{@code auction.getConsignment().getSellerMember()}에서 memberId를 미리 꺼내 담는다.
 */
public record AuctionEndedEvent(
    String eventId,
    Long auctionId,
    Long consignmentId,
    Long sellerMemberId,
    Long startingPrice,
    Long reservePrice,
    Long winningBidId,
    Long winnerMemberId,
    Long winningPrice,
    AuctionStatus auctionStatus,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime createdAt,
    LocalDateTime occurredAt)
    implements MessageQueueEvent {

  public static AuctionEndedEvent fromEntity(Auction auction, Bid winningBid) {
    return new AuctionEndedEvent(
        UUID.randomUUID().toString(),
        auction.getAuctionId(),
        auction.getConsignment().getConsignmentId(),
        auction.getConsignment().getSellerMember().getMemberId(),
        auction.getStartingPrice(),
        auction.getReservePrice(),
        auction.getWinningBidId(),
        winningBid != null ? winningBid.getMember().getMemberId() : null,
        auction.getWinningPrice(),
        auction.getAuctionStatus(),
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getCreatedAt(),
        LocalDateTime.now());
  }

  @Override
  public AggregateType aggregateType() {
    return AggregateType.AUCTION;
  }

  @Override
  public Long aggregateId() {
    return auctionId;
  }

  @Override
  public EventType eventType() {
    return EventType.AUCTION_ENDED;
  }
}
