package com.ootd.pickup.admin.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import java.time.LocalDateTime;

public record AdminAuctionDetailResponse(
    Long auctionId,
    Long consignmentId,
    Long sellerMemberId,
    String sellerLoginId,
    String sellerNickname,
    String cardName,
    String grade,
    String majorDefect,
    AuctionStatus auctionStatus,
    Long startingPrice,
    Long reservePrice,
    Long bidIncrement,
    Long winningPrice,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime createdAt) {

  public static AdminAuctionDetailResponse of(Auction auction, Certificate certificate) {
    Consignment consignment = auction.getConsignment();
    return new AdminAuctionDetailResponse(
        auction.getAuctionId(),
        consignment.getConsignmentId(),
        consignment.getSellerMember().getMemberId(),
        consignment.getSellerMember().getLoginId(),
        consignment.getSellerMember().getNickname(),
        consignment.getCard().getCardName(),
        certificate != null ? certificate.getGradeDisplay() : null,
        consignment.getMajorDefect(),
        auction.getAuctionStatus(),
        auction.getStartingPrice(),
        auction.getReservePrice(),
        auction.getBidIncrement(),
        auction.getWinningPrice(),
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getCreatedAt());
  }
}
