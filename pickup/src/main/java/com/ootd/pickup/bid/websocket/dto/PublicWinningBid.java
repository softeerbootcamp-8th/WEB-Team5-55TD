package com.ootd.pickup.bid.websocket.dto;

import com.ootd.pickup.auction.event.WinningBidSnapshot;
import com.ootd.pickup.global.util.NicknameMasker;
import java.time.LocalDateTime;

public record PublicWinningBid(
    Long bidId, String nicknameMasked, Long bidPrice, LocalDateTime createdAt) {

  public static PublicWinningBid fromEvent(WinningBidSnapshot winningBid) {
    return new PublicWinningBid(
        winningBid.bidId(),
        NicknameMasker.mask(winningBid.memberNickname()),
        winningBid.bidPrice(),
        winningBid.createdAt());
  }
}
