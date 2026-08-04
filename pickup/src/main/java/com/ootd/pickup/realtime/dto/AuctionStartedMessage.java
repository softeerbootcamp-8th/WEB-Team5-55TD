package com.ootd.pickup.realtime.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionStartedMessage(
    UUID eventId,
    AuctionRealtimeMessageType type,
    Long auctionId,
    LocalDateTime occurredAt,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {}
