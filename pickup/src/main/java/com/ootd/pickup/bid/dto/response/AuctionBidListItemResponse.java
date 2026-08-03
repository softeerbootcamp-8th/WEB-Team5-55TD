package com.ootd.pickup.bid.dto.response;

import com.ootd.pickup.bid.domain.Bid;
import java.time.LocalDateTime;

public record AuctionBidListItemResponse(
    Long bidId, String nicknameMasked, Long bidPrice, LocalDateTime createdAt, boolean isMine) {

  public static AuctionBidListItemResponse of(Bid bid, Long viewerMemberId) {
    return new AuctionBidListItemResponse(
        bid.getBidId(),
        maskNickname(bid.getMember().getNickname()),
        bid.getBidPrice(),
        bid.getCreatedAt(),
        viewerMemberId != null && viewerMemberId.equals(bid.getMember().getMemberId()));
  }

  private static String maskNickname(String nickname) {
    String prefix = nickname.substring(0, 3);
    String suffix = nickname.substring(nickname.length() - 2);
    return prefix + "***" + suffix;
  }
}
