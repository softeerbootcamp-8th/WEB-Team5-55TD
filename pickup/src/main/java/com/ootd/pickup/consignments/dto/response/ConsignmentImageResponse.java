package com.ootd.pickup.consignments.dto.response;

import com.ootd.pickup.consignments.domain.ConsignmentImage;

public record ConsignmentImageResponse(
        Long productImageId,
        int imageOrder,
        String imageUrl
) {
    public static ConsignmentImageResponse from(ConsignmentImage consignmentImage) {
        return new ConsignmentImageResponse(
                consignmentImage.getConsignmentImageId(),
                consignmentImage.getImageOrder(),
                consignmentImage.getImageUrl()
        );
    }
}
