package com.ootd.pickup.bid.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record BidPlacedEvent(
    UUID eventId,
    Long auctionId,
    Long bidId,
    String nicknameMasked,
    Long bidPrice,
    LocalDateTime createdAt,
    LocalDateTime occurredAt) {}
