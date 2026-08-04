package com.ootd.pickup.admin.dto.response;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;

public record AdminConsignmentDetailResponse(
    Long consignmentId,
    Long sellerMemberId,
    String sellerLoginId,
    String sellerNickname,
    String cardName,
    String grade,
    ConsignmentStatus status,
    String majorDefect) {

  public static AdminConsignmentDetailResponse of(
      Consignment consignment, Certificate certificate) {
    return new AdminConsignmentDetailResponse(
        consignment.getConsignmentId(),
        consignment.getSellerMember().getMemberId(),
        consignment.getSellerMember().getLoginId(),
        consignment.getSellerMember().getNickname(),
        consignment.getCard().getCardName(),
        certificate != null ? certificate.getGradeDisplay() : null,
        consignment.getStatus(),
        consignment.getMajorDefect());
  }
}
