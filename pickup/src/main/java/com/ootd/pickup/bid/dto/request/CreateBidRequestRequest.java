package com.ootd.pickup.bid.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBidRequestRequest(@NotNull @Positive Long bidPrice) {}
