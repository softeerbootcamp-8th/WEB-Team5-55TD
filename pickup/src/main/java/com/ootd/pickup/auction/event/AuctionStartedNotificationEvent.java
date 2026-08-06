package com.ootd.pickup.auction.event;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 경매 시작(SCHEDULED → ONGOING) 사건.
 *
 * <p>구독 중인 모든 App 서버가 각자 WebSocket 세션에 전달해야 하므로 {@link NotificationEvent}로 분류한다. 유실이 허용되며 Outbox를
 * 거치지 않고 Redis Pub/Sub으로 즉시 발행된다.
 */
public record AuctionStartedNotificationEvent(
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
    implements NotificationEvent {

  public static AuctionStartedNotificationEvent fromEntity(Auction auction) {
    return new AuctionStartedNotificationEvent(
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
  public EventType eventType() {
    return EventType.AUCTION_STARTED;
  }
}
