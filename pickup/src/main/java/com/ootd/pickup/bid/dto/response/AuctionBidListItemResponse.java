package com.ootd.pickup.bid.dto.response;

import com.ootd.pickup.bid.domain.Bid;
import java.time.LocalDateTime;

public record AuctionBidListItemResponse(
    Long bidId, String nickname, Long bidPrice, LocalDateTime createdAt, boolean isMine) {

  public static AuctionBidListItemResponse of(Bid bid, Long viewerMemberId) {
    return new AuctionBidListItemResponse(
        bid.getBidId(),
        bid.getMember().getNickname(),
        bid.getBidPrice(),
        bid.getCreatedAt(),
        viewerMemberId != null && viewerMemberId.equals(bid.getMember().getMemberId()));
  }
}
