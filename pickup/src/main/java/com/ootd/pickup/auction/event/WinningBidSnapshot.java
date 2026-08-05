package com.ootd.pickup.auction.event;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import java.time.LocalDateTime;

/**
 * {@link AuctionBidUpdatedEvent}/{@link AuctionEndedNotificationEvent}에 담기는 최고(낙찰) 입찰 스냅샷.
 *
 * <p>소비자는 다른 프로세스에서 실행되므로 {@link Bid} 엔티티 대신 식별자와 원시값만 옮긴다.
 */
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
