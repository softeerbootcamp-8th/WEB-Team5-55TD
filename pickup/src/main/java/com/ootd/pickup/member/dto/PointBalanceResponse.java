package com.ootd.pickup.member.dto;

public record PointBalanceResponse(
    long pointBalance, long reservedPointBalance, long availablePointBalance) {}
