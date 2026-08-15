package com.ootd.pickup.auction.cache;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

/**
 * 입찰 요청 생성 API가 DB 락 없이 참고하는 경매 상태의 스냅샷.
 *
 * <p>{@link #sellerMemberId()}는 경매 생성 후 바뀌지 않는 값이라 캐시가 지연되더라도 무효화될 일이 없다. 나머지 필드는 {@link
 * com.ootd.pickup.bid.service.BidService#placeBid}가 실제로 커밋한 값이 반영되기까지 지연될 수 있으며, 그 지연은 사전 필터를 실제보다
 * 관대하게만 만든다 — 최종 검증은 여전히 {@code placeBid}가 락 하에 수행한다.
 */
public record AuctionSnapshot(
    Long auctionId,
    Long currentPrice,
    Long bidIncrement,
    AuctionStatus auctionStatus,
    LocalDateTime endedAt,
    Long sellerMemberId) {

  public static AuctionSnapshot fromEntity(Auction auction) {
    return new AuctionSnapshot(
        auction.getAuctionId(),
        auction.getCurrentPrice(),
        auction.getBidIncrement(),
        auction.getAuctionStatus(),
        auction.getEndedAt(),
        auction.getConsignment().getSellerMember().getMemberId());
  }
}
