package com.ootd.pickup.bid.websocket.dto;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.BidRequestSucceededNotificationEvent;
import java.time.LocalDateTime;

public record BidRequestSucceededMessage(
    String eventId,
    String type,
    Long auctionId,
    Long bidRequestId,
    AuctionStatus auctionStatus,
    Long currentPrice,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    PublicWinningBid latestBid,
    LocalDateTime occurredAt) {

  public static BidRequestSucceededMessage fromEvent(BidRequestSucceededNotificationEvent event) {
    return new BidRequestSucceededMessage(
        event.eventId(),
        event.eventType().name(),
        event.auctionId(),
        event.bidRequestId(),
        event.auctionStatus(),
        event.winningPrice(),
        event.startedAt(),
        event.endedAt(),
        PublicWinningBid.fromEvent(event.winningBid()),
        event.occurredAt());
  }
}
