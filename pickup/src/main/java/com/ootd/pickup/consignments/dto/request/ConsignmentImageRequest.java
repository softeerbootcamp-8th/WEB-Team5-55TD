package com.ootd.pickup.consignments.dto.request;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsignmentImageRequest(
        @NotNull Integer imageOrder,
        @NotBlank String imageUrl
) {
    public ConsignmentImage toEntity(Consignment consignment) {
        return ConsignmentImage.builder()
                .consignment(consignment)
                .imageOrder(imageOrder)
                .imageUrl(imageUrl)
                .build();
    }
}
