package com.ootd.pickup.realtime.dto;

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
    String prefix = nickname.substring(0, 3);
    String suffix = nickname.substring(nickname.length() - 2);
    return prefix + "***" + suffix;
  }
}
