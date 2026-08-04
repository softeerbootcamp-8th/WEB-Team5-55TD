package com.ootd.pickup.realtime.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionEndedMessage(
    UUID eventId,
    String type,
    Long auctionId,
    LocalDateTime occurredAt,
    String status,
    Long finalPrice,
    LocalDateTime endedAt) {}
