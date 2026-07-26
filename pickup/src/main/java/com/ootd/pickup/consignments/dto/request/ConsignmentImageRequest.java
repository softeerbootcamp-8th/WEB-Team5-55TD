package com.ootd.pickup.consignments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsignmentImageRequest(
        @NotNull Integer imageOrder,
        @NotBlank String imageUrl
) {
}
