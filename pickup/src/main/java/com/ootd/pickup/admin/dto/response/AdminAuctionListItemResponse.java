package com.ootd.pickup.admin.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import java.time.LocalDateTime;

public record AdminAuctionListItemResponse(
    Long auctionId,
    Long consignmentId,
    String cardName,
    Long sellerMemberId,
    String sellerNickname,
    AuctionStatus auctionStatus,
    Long startingPrice,
    Long reservePrice,
    Long winningPrice,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime createdAt) {

  public static AdminAuctionListItemResponse fromEntity(Auction auction) {
    Consignment consignment = auction.getConsignment();
    return new AdminAuctionListItemResponse(
        auction.getAuctionId(),
        consignment.getConsignmentId(),
        consignment.getCard().getCardName(),
        consignment.getSellerMember().getMemberId(),
        consignment.getSellerMember().getNickname(),
        auction.getAuctionStatus(),
        auction.getStartingPrice(),
        auction.getReservePrice(),
        auction.getWinningPrice(),
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getCreatedAt());
  }
}
