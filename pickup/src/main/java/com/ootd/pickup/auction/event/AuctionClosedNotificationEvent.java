package com.ootd.pickup.auction.event;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 경매 종료(ONGOING → WON/PASSED) 사건의 알림 계열.
 *
 * <p>{@link AuctionClosedMessageQueueEvent}와 같은 사건이지만 필요한 보장이 다르다. 이쪽은 경매를 보고 있는 모든 App 서버가 각자 세션에
 * 전달해야 하므로 구독한 전부가 받아야 하고, 유실이 허용되는 대신 즉시 나가야 한다. Outbox를 거치지 않고 Redis Pub/Sub으로 발행된다.
 *
 * <p>정산은 이쪽이 아니라 메시지 큐 계열이 담당한다. 여기서 정산을 하면 구독한 인스턴스마다 한 번씩 돌아 중복 지급이 된다.
 */
public record AuctionClosedNotificationEvent(
    String eventId,
    Long auctionId,
    Long consignmentId,
    Long startingPrice,
    Long winningBidId,
    Long winningPrice,
    AuctionStatus auctionStatus,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    LocalDateTime occurredAt)
    implements NotificationEvent {

  /**
   * 종료된 경매로 알림을 만든다.
   *
   * <p>리저브를 담지 않는다. 셀러가 정한 비공개 값이고, 이 알림은 경매를 보고 있는 모든 클라이언트에게 흘러가므로 필요하지 않은 경로로 내보낼 이유가 없다. 낙찰 여부는
   * {@code auctionStatus}로 이미 전달된다.
   *
   * @param auction 종료 전이가 끝난 경매. {@code consignment}가 로드돼 있어야 한다
   * @return 새 식별자가 부여된 알림
   */
  public static AuctionClosedNotificationEvent fromEntity(Auction auction) {
    return new AuctionClosedNotificationEvent(
        UUID.randomUUID().toString(),
        auction.getAuctionId(),
        auction.getConsignment().getConsignmentId(),
        auction.getStartingPrice(),
        auction.getWinningBidId(),
        auction.getWinningPrice(),
        auction.getAuctionStatus(),
        auction.getStartedAt(),
        auction.getEndedAt(),
        LocalDateTime.now());
  }

  @Override
  public AggregateType aggregateType() {
    return AggregateType.AUCTION;
  }

  @Override
  public Long aggregateId() {
    return auctionId;
  }

  @Override
  public EventType eventType() {
    return EventType.AUCTION_CLOSED;
  }
}
