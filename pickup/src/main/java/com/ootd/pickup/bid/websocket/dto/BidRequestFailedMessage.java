package com.ootd.pickup.bid.websocket.dto;

import com.ootd.pickup.bid.event.BidRequestFailedNotificationEvent;
import java.time.LocalDateTime;

public record BidRequestFailedMessage(
    String eventId,
    String type,
    Long auctionId,
    Long bidRequestId,
    Long bidPrice,
    String failureCode,
    String failureMessage,
    LocalDateTime occurredAt) {

  public static BidRequestFailedMessage fromEvent(BidRequestFailedNotificationEvent event) {
    return new BidRequestFailedMessage(
        event.eventId(),
        event.eventType().name(),
        event.auctionId(),
        event.bidRequestId(),
        event.bidPrice(),
        event.failureCode(),
        event.failureMessage(),
        event.occurredAt());
  }
}
