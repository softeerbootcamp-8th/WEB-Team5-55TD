package com.ootd.pickup.bid.dto.response;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import java.time.LocalDateTime;

public record CreateBidRequestResponse(
    Long bidRequestId,
    Long auctionId,
    Long memberId,
    Long bidPrice,
    BidRequestStatus status,
    LocalDateTime createdAt) {

  public static CreateBidRequestResponse from(BidRequest bidRequest) {
    return new CreateBidRequestResponse(
        bidRequest.getBidRequestId(),
        bidRequest.getAuctionId(),
        bidRequest.getMemberId(),
        bidRequest.getBidPrice(),
        bidRequest.getStatus(),
        bidRequest.getCreatedAt());
  }
}
