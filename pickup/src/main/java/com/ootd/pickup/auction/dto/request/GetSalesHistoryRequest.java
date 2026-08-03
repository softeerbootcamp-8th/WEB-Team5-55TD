package com.ootd.pickup.auction.dto.request;

public record GetSalesHistoryRequest(String cursor, Integer size, String status) {}
