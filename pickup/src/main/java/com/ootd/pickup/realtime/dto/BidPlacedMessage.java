package com.ootd.pickup.realtime.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record BidPlacedMessage(
    UUID eventId,
    String type,
    Long auctionId,
    LocalDateTime occurredAt,
    Long bidId,
    String nicknameMasked,
    Long bidPrice,
    LocalDateTime createdAt) {}
