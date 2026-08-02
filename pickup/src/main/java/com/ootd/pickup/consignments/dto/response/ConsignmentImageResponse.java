package com.ootd.pickup.consignments.dto.response;

import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.images.service.ImageUrlResolver;

public record ConsignmentImageResponse(Long productImageId, int imageOrder, String imageUrl) {
  public static ConsignmentImageResponse from(
      ConsignmentImage consignmentImage, ImageUrlResolver imageUrlResolver) {
    return new ConsignmentImageResponse(
        consignmentImage.getConsignmentImageId(),
        consignmentImage.getImageOrder(),
        imageUrlResolver.resolve(consignmentImage.getObjectKey()));
  }
}
