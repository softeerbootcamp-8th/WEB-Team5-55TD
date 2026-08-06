package com.ootd.pickup.realtime.dto;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionBidUpdatedEvent;
import java.time.LocalDateTime;

public record AuctionBidUpdatedMessage(
    String eventId,
    String type,
    Long auctionId,
    AuctionStatus auctionStatus,
    Long currentPrice,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    PublicWinningBid latestBid,
    LocalDateTime occurredAt) {

  public static AuctionBidUpdatedMessage fromEvent(AuctionBidUpdatedEvent event) {
    return new AuctionBidUpdatedMessage(
        event.eventId(),
        event.eventType().name(),
        event.auctionId(),
        event.auctionStatus(),
        event.winningPrice(),
        event.startedAt(),
        event.endedAt(),
        PublicWinningBid.fromEvent(event.winningBid()),
        event.occurredAt());
  }
}
