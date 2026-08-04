package com.ootd.pickup.realtime.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionExtendedMessage(
    UUID eventId,
    String type,
    Long auctionId,
    LocalDateTime occurredAt,
    LocalDateTime previousEndedAt,
    LocalDateTime endedAt) {}
