package com.ootd.pickup.consignments.dto.response;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionSummary;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import java.time.LocalDateTime;

public record GetMyConsignmentsResponse(
    Long consignmentId,
    Long auctionId,
    GetCardDetailResponse card,
    Long sellerMemberId,
    String majorDefect,
    ConsignmentStatus status,
    AuctionStatus auctionStatus,
    LocalDateTime auctionStartedAt,
    LocalDateTime auctionEndedAt,
    CertificateResponse certificate,
    String thumbnailUrl) {
  public static GetMyConsignmentsResponse fromEntity(
      Consignment consignment,
      Long sellerMemberId,
      Certificate certificate,
      AuctionSummary auctionSummary,
      String thumbnailUrl) {
    return new GetMyConsignmentsResponse(
        consignment.getConsignmentId(),
        auctionSummary == null ? null : auctionSummary.auctionId(),
        GetCardDetailResponse.from(consignment.getCard()),
        sellerMemberId,
        consignment.getMajorDefect(),
        consignment.getStatus(),
        auctionSummary == null ? null : auctionSummary.auctionStatus(),
        auctionSummary == null ? null : auctionSummary.startedAt(),
        auctionSummary == null ? null : auctionSummary.endedAt(),
        CertificateResponse.from(certificate),
        thumbnailUrl);
  }
}
