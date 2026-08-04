package com.ootd.pickup.admin.dto.request;

import java.util.List;

public record AdminSearchAuctionsRequest(String q, List<String> status, Long sellerMemberId) {}
