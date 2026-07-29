package com.ootd.pickup.consignments.dto.response;

import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;

public record RegisterConsignmentResponse(
    Long consignmentId,
    SearchCardsResponse card,
    Long sellerMemberId,
    String majorDefect,
    ConsignmentStatus status,
    CertificateResponse certificate) {
  public static RegisterConsignmentResponse of(Consignment consignment, Certificate certificate) {
    return new RegisterConsignmentResponse(
        consignment.getConsignmentId(),
        SearchCardsResponse.from(consignment.getCard()),
        consignment.getSellerMemberId(),
        consignment.getMajorDefect(),
        consignment.getStatus(),
        CertificateResponse.from(certificate));
  }
}
