package com.ootd.pickup.auction.dto.response;

public record SellerStatsResponse(
    long registeredConsignments,
    long scheduledAuctions,
    long ongoingAuctions,
    long wonConsignments
) {}
