package com.ootd.pickup.auction.event;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import java.time.LocalDateTime;

public record WinningBidSnapshot(
    Long bidId,
    Long memberId,
    String memberNickname,
    Long bidPrice,
    BidStatus bidStatus,
    LocalDateTime createdAt) {

  public static WinningBidSnapshot fromEntity(Bid bid) {
    return new WinningBidSnapshot(
        bid.getBidId(),
        bid.getMember().getMemberId(),
        bid.getMember().getNickname(),
        bid.getBidPrice(),
        bid.getBidStatus(),
        bid.getCreatedAt());
  }
}
