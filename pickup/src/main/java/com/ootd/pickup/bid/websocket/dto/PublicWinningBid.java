package com.ootd.pickup.bid.websocket.dto;

import com.ootd.pickup.auction.event.WinningBidSnapshot;
import java.time.LocalDateTime;

public record PublicWinningBid(
    Long bidId, String nickname, Long bidPrice, LocalDateTime createdAt) {

  public static PublicWinningBid fromEvent(WinningBidSnapshot winningBid) {
    return new PublicWinningBid(
        winningBid.bidId(),
        winningBid.memberNickname(),
        winningBid.bidPrice(),
        winningBid.createdAt());
  }
}
