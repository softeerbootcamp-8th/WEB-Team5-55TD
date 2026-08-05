package com.ootd.pickup.auction.event;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 경매 종료(ONGOING → WON/PASSED) 사건 — 실시간 알림용.
 *
 * <p>구독 중인 모든 App 서버가 각자 WebSocket 세션에 전달해야 하므로 {@link NotificationEvent}로 분류한다. 유실이 허용되며 Outbox를
 * 거치지 않고 Redis Pub/Sub으로 즉시 발행된다.
 *
 * <p>같은 사건을 정산 컨슈머에게 정확히 한 번 전달하는 용도로는 {@link AuctionEndedMessageQueueEvent}를 쓴다. 이 이벤트는 "화면에 경매가
 * 끝났다고 알린다"는 목적만 가지므로, 정산에만 필요한 {@code winnerMemberId}/{@code sellerMemberId}는 담지 않고 {@link
 * AuctionBidUpdatedEvent}와 같은 모양(스냅샷 + {@link WinningBidSnapshot})을 유지한다.
 */
public record AuctionEndedNotificationEvent(
    String eventId,
    Long auctionId,
    Long consignmentId,
    Long startingPrice,
    Long reservePrice,
    Long winningPrice,
    AuctionStatus auctionStatus,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime createdAt,
    WinningBidSnapshot winningBid,
    LocalDateTime occurredAt)
    implements NotificationEvent {

  public static AuctionEndedNotificationEvent fromEntity(Auction auction, Bid winningBid) {
    return new AuctionEndedNotificationEvent(
        UUID.randomUUID().toString(),
        auction.getAuctionId(),
        auction.getConsignment().getConsignmentId(),
        auction.getStartingPrice(),
        auction.getReservePrice(),
        auction.getWinningPrice(),
        auction.getAuctionStatus(),
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getCreatedAt(),
        winningBid != null ? WinningBidSnapshot.fromEntity(winningBid) : null,
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
