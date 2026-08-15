package com.ootd.pickup.auction.cache.event;

import com.ootd.pickup.auction.cache.AuctionSnapshot;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

/**
 * 경매 스냅샷 캐시를 갱신해야 한다는 내부 신호.
 *
 * <p>{@link com.ootd.pickup.global.event.DomainEvent}를 구현하지 않는다 — {@code NotificationEvent}에 얹으면
 * 클라이언트 대상 Redis Pub/Sub 스키마를 오염시키고 인스턴스마다 캐시 쓰기가 중복된다. 대신 스프링 {@code ApplicationEventPublisher}로
 * 직접 발행해 캐시 갱신 목적으로만 소비되는 별도 채널로 둔다.
 */
public record AuctionSnapshotChangedEvent(
    Long auctionId,
    Long currentPrice,
    Long bidIncrement,
    AuctionStatus auctionStatus,
    LocalDateTime endedAt,
    Long sellerMemberId) {

  public AuctionSnapshot toSnapshot() {
    return new AuctionSnapshot(
        auctionId, currentPrice, bidIncrement, auctionStatus, endedAt, sellerMemberId);
  }
}
