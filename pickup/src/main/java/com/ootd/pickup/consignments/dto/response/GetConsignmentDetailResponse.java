package com.ootd.pickup.consignments.dto.response;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionSummary;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.CardState;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.images.service.ImageUrlResolver;
import java.time.LocalDateTime;
import java.util.List;

public record GetConsignmentDetailResponse(
    Long consignmentId,
    GetCardDetailResponse card,
    String sellerMemberNickname,
    CardState cardState,
    String majorDefect,
    ConsignmentStatus status,
    AuctionStatus auctionStatus,
    LocalDateTime auctionStartedAt,
    LocalDateTime auctionEndedAt,
    CertificateResponse certificate,
    List<ConsignmentImageResponse> images,
    boolean auctionRegistered) {
  public static GetConsignmentDetailResponse of(
      Consignment consignment,
      Certificate certificate,
      List<ConsignmentImage> images,
      String sellerMemberNickname,
      ImageUrlResolver imageUrlResolver,
      AuctionSummary auctionSummary) {
    return new GetConsignmentDetailResponse(
        consignment.getConsignmentId(),
        GetCardDetailResponse.from(consignment.getCard()),
        sellerMemberNickname,
        consignment.getCardState(),
        consignment.getMajorDefect(),
        consignment.getStatus(),
        auctionSummary == null ? null : auctionSummary.auctionStatus(),
        auctionSummary == null ? null : auctionSummary.startedAt(),
        auctionSummary == null ? null : auctionSummary.endedAt(),
        CertificateResponse.from(certificate),
        images.stream()
            .map(image -> ConsignmentImageResponse.from(image, imageUrlResolver))
            .toList(),
        consignment.getStatus() != ConsignmentStatus.REGISTERABLE);
  }
}
