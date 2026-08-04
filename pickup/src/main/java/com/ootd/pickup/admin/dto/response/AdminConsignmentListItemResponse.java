package com.ootd.pickup.admin.dto.response;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;

public record AdminConsignmentListItemResponse(
    Long consignmentId,
    String cardName,
    Long sellerMemberId,
    String sellerNickname,
    ConsignmentStatus status,
    String majorDefect) {

  public static AdminConsignmentListItemResponse fromEntity(Consignment consignment) {
    return new AdminConsignmentListItemResponse(
        consignment.getConsignmentId(),
        consignment.getCard().getCardName(),
        consignment.getSellerMember().getMemberId(),
        consignment.getSellerMember().getNickname(),
        consignment.getStatus(),
        consignment.getMajorDefect());
  }
}
