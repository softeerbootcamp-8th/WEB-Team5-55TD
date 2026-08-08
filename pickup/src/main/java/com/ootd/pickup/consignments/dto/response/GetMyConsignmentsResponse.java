package com.ootd.pickup.consignments.dto.response;

import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;

public record GetMyConsignmentsResponse(
    Long consignmentId,
    Long auctionId,
    GetCardDetailResponse card,
    Long sellerMemberId,
    String majorDefect,
    ConsignmentStatus status,
    CertificateResponse certificate,
    String thumbnailUrl) {
  public static GetMyConsignmentsResponse fromEntity(
      Consignment consignment,
      Long sellerMemberId,
      Certificate certificate,
      Long auctionId,
      String thumbnailUrl) {
    return new GetMyConsignmentsResponse(
        consignment.getConsignmentId(),
        auctionId,
        GetCardDetailResponse.from(consignment.getCard()),
        sellerMemberId,
        consignment.getMajorDefect(),
        consignment.getStatus(),
        CertificateResponse.from(certificate),
        thumbnailUrl);
  }
}
