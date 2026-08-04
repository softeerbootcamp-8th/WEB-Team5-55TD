package com.ootd.pickup.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminCancelAuctionRequest(@NotBlank(message = "사유는 필수입니다.") String reason) {}
