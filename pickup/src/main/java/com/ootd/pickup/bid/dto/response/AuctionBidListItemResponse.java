package com.ootd.pickup.bid.dto.response;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.global.util.WithdrawnMemberDisplay;
import com.ootd.pickup.images.service.ImageUrlResolver;
import java.time.LocalDateTime;

public record AuctionBidListItemResponse(
    Long bidId,
    String nickname,
    String profileImageUrl,
    Long bidPrice,
    LocalDateTime createdAt,
    boolean isMine) {

  public static AuctionBidListItemResponse of(
      Bid bid, Long viewerMemberId, ImageUrlResolver imageUrlResolver) {
    return new AuctionBidListItemResponse(
        bid.getBidId(),
        WithdrawnMemberDisplay.resolveNickname(bid.getMember(), bid.getBidderNicknameSnapshot()),
        imageUrlResolver.resolve(bid.getMember().getProfileImageObjectKey()),
        bid.getBidPrice(),
        bid.getCreatedAt(),
        viewerMemberId != null && viewerMemberId.equals(bid.getMember().getMemberId()));
  }
}
