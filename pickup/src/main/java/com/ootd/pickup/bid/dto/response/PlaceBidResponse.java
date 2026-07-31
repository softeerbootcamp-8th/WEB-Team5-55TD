package com.ootd.pickup.bid.dto.response;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import java.time.LocalDateTime;

public record PlaceBidResponse(
    Long bidId,
    Long auctionId,
    Long memberId,
    Long bidPrice,
    BidStatus bidStatus,
    LocalDateTime createdAt) {

  public static PlaceBidResponse from(Bid bid) {
    return new PlaceBidResponse(
        bid.getBidId(),
        bid.getAuction().getAuctionId(),
        bid.getMember().getMemberId(),
        bid.getBidPrice(),
        bid.getBidStatus(),
        bid.getCreatedAt());
  }
}
