package com.ootd.pickup.auction.event;

import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionEndedEvent(
    UUID eventId,
    Long auctionId,
    AuctionStatus status,
    Long finalPrice,
    LocalDateTime endedAt,
    LocalDateTime occurredAt) {}
