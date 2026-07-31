package com.ootd.pickup.consignments.dto.request;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import jakarta.validation.constraints.NotBlank;

public record ConsignmentImageRequest(@NotBlank String imageUrl) {
  public ConsignmentImage toEntity(Consignment consignment, int imageOrder) {
    return ConsignmentImage.builder()
        .consignment(consignment)
        .imageOrder(imageOrder)
        .imageUrl(imageUrl)
        .build();
  }
}
