package com.ootd.pickup.bid.websocket.dto;

import com.ootd.pickup.auction.event.WinningBidSnapshot;
import java.time.LocalDateTime;

public record PublicWinningBid(
    Long bidId, String nicknameMasked, Long bidPrice, LocalDateTime createdAt) {

  public static PublicWinningBid fromEvent(WinningBidSnapshot winningBid) {
    return new PublicWinningBid(
        winningBid.bidId(),
        maskNickname(winningBid.memberNickname()),
        winningBid.bidPrice(),
        winningBid.createdAt());
  }

  private static String maskNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      return "***";
    }

    int[] codePoints = nickname.codePoints().toArray();
    if (codePoints.length == 1) {
      return "***";
    }

    String first = new String(codePoints, 0, 1);
    String last = new String(codePoints, codePoints.length - 1, 1);
    return first + "***" + last;
  }
}
