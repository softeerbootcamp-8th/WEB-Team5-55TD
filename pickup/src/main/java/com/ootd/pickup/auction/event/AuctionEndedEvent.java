package com.ootd.pickup.auction.event;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 경매 종료(ONGOING → WON/PASSED) 사건.
 *
 * <p>후속 처리(정산 등)가 정확히 한 번만 실행돼야 하므로 {@link MessageQueueEvent}로 분류한다. Outbox에 먼저 저장되고 별도 Relay가 SQS
 * FIFO 큐로 옮긴다.
 */
public record AuctionEndedEvent(
    String eventId,
    Long auctionId,
    Long consignmentId,
    Long startingPrice,
    Long reservePrice,
    Long winningBidId,
    Long winningPrice,
    AuctionStatus auctionStatus,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime createdAt,
    LocalDateTime occurredAt)
    implements MessageQueueEvent {

  public static AuctionEndedEvent fromEntity(Auction auction) {
    return new AuctionEndedEvent(
        UUID.randomUUID().toString(),
        auction.getAuctionId(),
        auction.getConsignment().getConsignmentId(),
        auction.getStartingPrice(),
        auction.getReservePrice(),
        auction.getWinningBidId(),
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
  public String eventType() {
    return "AUCTION_ENDED";
  }
}
