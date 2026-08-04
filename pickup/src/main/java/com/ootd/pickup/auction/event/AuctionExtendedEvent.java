package com.ootd.pickup.auction.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionExtendedEvent(
    UUID eventId,
    Long auctionId,
    LocalDateTime previousEndedAt,
    LocalDateTime endedAt,
    LocalDateTime occurredAt) {}
