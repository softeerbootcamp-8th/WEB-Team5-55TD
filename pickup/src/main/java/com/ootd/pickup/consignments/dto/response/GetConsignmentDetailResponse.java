package com.ootd.pickup.consignments.dto.response;

import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.images.service.ImageUrlResolver;
import java.util.List;

public record GetConsignmentDetailResponse(
    Long consignmentId,
    GetCardDetailResponse card,
    String sellerMemberNickname,
    String majorDefect,
    ConsignmentStatus status,
    CertificateResponse certificate,
    List<ConsignmentImageResponse> images,
    boolean auctionRegistered) {
  public static GetConsignmentDetailResponse of(
      Consignment consignment,
      Certificate certificate,
      List<ConsignmentImage> images,
      String sellerMemberNickname,
      ImageUrlResolver imageUrlResolver) {
    return new GetConsignmentDetailResponse(
        consignment.getConsignmentId(),
        GetCardDetailResponse.from(consignment.getCard()),
        sellerMemberNickname,
        consignment.getMajorDefect(),
        consignment.getStatus(),
        CertificateResponse.from(certificate),
        images.stream()
            .map(image -> ConsignmentImageResponse.from(image, imageUrlResolver))
            .toList(),
        consignment.getStatus() != ConsignmentStatus.REGISTERABLE);
  }
}
