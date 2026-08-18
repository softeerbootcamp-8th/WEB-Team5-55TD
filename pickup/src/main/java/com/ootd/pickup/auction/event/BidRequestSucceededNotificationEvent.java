package com.ootd.pickup.auction.event;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 입찰(동기 요청 또는 비동기 입찰 요청 처리 결과)로 현재가가 갱신된 사건.
 *
 * <p>구독 중인 모든 App 서버가 각자 WebSocket 세션에 전달해야 하므로 {@link NotificationEvent}로 분류한다. 유실이 허용되며 Outbox를
 * 거치지 않고 Redis Pub/Sub으로 즉시 발행된다.
 *
 * <p>{@link #bidRequestId()}는 비동기 입찰 요청(POST .../bid-requests)을 통해 처리된 경우에만 값이 있다. 기존 동기 엔드포인트(POST
 * .../bids)를 통한 입찰은 이 값이 {@code null}이며, 요청자 본인은 이미 REST 응답으로 결과를 알기 때문에 화면에서 별도 처리가 필요 없다.
 */
public record BidRequestSucceededNotificationEvent(
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
    Long bidRequestId,
    LocalDateTime occurredAt)
    implements NotificationEvent {

  public static BidRequestSucceededNotificationEvent fromEntity(
      Auction auction, Bid winningBid, Long bidRequestId) {
    return fromEntity(auction, winningBid, bidRequestId, null);
  }

  public static BidRequestSucceededNotificationEvent fromEntity(
      Auction auction, Bid winningBid, Long bidRequestId, String profileImageUrl) {
    return new BidRequestSucceededNotificationEvent(
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
        WinningBidSnapshot.fromEntity(winningBid, profileImageUrl),
        bidRequestId,
        LocalDateTime.now(ZoneOffset.UTC));
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
    return EventType.BID_REQUEST_SUCCEEDED;
  }
}
