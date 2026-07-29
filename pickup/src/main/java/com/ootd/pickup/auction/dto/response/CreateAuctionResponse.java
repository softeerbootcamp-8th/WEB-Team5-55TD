package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

public record CreateAuctionResponse(
    Long auctionId,
    Long consignmentId,
    AuctionStatus auctionStatus,
    Long startingPrice,
    Long bidIncrement,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Long winningBidId,
    Long winningPrice,
    LocalDateTime createdAt) {

  public static CreateAuctionResponse from(Auction auction) {
    return new CreateAuctionResponse(
        auction.getAuctionId(),
        auction.getConsignment().getConsignmentId(),
        auction.getAuctionStatus(),
        auction.getStartingPrice(),
        auction.getMinimumBidIncrement(),
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getWinningBidId(),
        auction.getWinningPrice(),
        auction.getCreatedAt());
  }
}
