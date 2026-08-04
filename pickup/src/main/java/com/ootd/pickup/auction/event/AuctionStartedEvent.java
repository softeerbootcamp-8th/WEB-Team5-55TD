package com.ootd.pickup.auction.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionStartedEvent(
    UUID eventId,
    Long auctionId,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime occurredAt) {}
