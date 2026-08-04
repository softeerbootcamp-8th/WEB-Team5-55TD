package com.ootd.pickup.admin.dto.request;

import java.util.List;

public record AdminSearchConsignmentsRequest(String q, List<String> status, Long sellerMemberId) {}
