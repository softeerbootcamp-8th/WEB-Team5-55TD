package com.ootd.pickup.consignments.dto.request;

import com.ootd.pickup.global.dto.request.CursorPageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GetMyConsignmentsRequest(@NotBlank String status, Long cursor, @NotNull Integer size)
    implements CursorPageRequest {}
