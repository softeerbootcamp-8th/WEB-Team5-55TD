package com.ootd.pickup.bid.dto.response;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import java.time.LocalDateTime;

public record BidRequestResultResponse(
    Long bidRequestId,
    Long auctionId,
    Long bidPrice,
    BidRequestStatus status,
    String failureCode,
    String failureMessage,
    LocalDateTime processedAt) {

  public static BidRequestResultResponse from(BidRequest bidRequest) {
    return new BidRequestResultResponse(
        bidRequest.getBidRequestId(),
        bidRequest.getAuctionId(),
        bidRequest.getBidPrice(),
        bidRequest.getStatus(),
        bidRequest.getFailureCode(),
        bidRequest.getFailureMessage(),
        bidRequest.getProcessedAt());
  }
}
