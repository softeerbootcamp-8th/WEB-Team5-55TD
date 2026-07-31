package com.ootd.pickup.auction.dto.request;

import java.util.List;

public record SearchAuctionsRequest(
    String q, List<String> status, String sort, Integer limit, String cursor, Integer size) {}
