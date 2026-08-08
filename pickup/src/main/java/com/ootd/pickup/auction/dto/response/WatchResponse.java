package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.auction.domain.Watch;
import java.time.LocalDateTime;

public record WatchResponse(Long watchId, Long memberId, Long auctionId, LocalDateTime createdAt) {

  public static WatchResponse from(Watch watch) {
    return new WatchResponse(
        watch.getWatchId(),
        watch.getMember().getMemberId(),
        watch.getAuction().getAuctionId(),
        watch.getCreatedAt());
  }
}
