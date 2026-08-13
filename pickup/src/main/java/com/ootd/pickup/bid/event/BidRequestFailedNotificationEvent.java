package com.ootd.pickup.bid.event;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 비동기 입찰 요청 처리가 실패한 사건.
 *
 * <p>요청한 회원 본인에게만 전달해야 하므로(다른 회원에게 실패 사유를 노출하지 않음) WebSocket 발행 단계에서 브로드캐스트가 아니라 {@link
 * #memberId()}로 유니캐스트한다. 채널 자체는 다른 경매 알림과 동일한 {@link AggregateType#AUCTION} 패턴을 재사용한다 — 회원 단위 라우팅은
 * 이 이벤트를 소비하는 WebSocket 퍼블리셔가 담당한다.
 */
public record BidRequestFailedNotificationEvent(
    String eventId,
    Long auctionId,
    Long memberId,
    Long bidRequestId,
    Long bidPrice,
    String failureCode,
    String failureMessage,
    LocalDateTime occurredAt)
    implements NotificationEvent {

  public static BidRequestFailedNotificationEvent from(
      BidRequestCreatedMessageQueueEvent event, PickUpException exception) {
    return new BidRequestFailedNotificationEvent(
        UUID.randomUUID().toString(),
        event.auctionId(),
        event.memberId(),
        event.bidRequestId(),
        event.bidPrice(),
        exception.getExceptionCodeName(),
        exception.getMessage(),
        LocalDateTime.now(ZoneOffset.UTC));
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
    return EventType.BID_REQUEST_FAILED;
  }
}
