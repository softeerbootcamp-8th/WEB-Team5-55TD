package com.ootd.pickup.bid.event;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 입찰 요청이 접수된 사건.
 *
 * <p>실제 입찰 처리(락 획득·검증·저장)가 정확히 한 번만 일어나야 하므로 {@link MessageQueueEvent}로 분류한다. Outbox를 거쳐 SQS FIFO
 * 큐로 전달되며, {@link #aggregateId()}가 경매 id이므로 같은 경매의 요청은 같은 메시지 그룹으로 묶여 순서대로 처리된다.
 */
public record BidRequestCreatedMessageQueueEvent(
    String eventId,
    Long bidRequestId,
    Long auctionId,
    Long memberId,
    Long bidPrice,
    LocalDateTime occurredAt)
    implements MessageQueueEvent {

  public static BidRequestCreatedMessageQueueEvent fromEntity(BidRequest bidRequest) {
    return new BidRequestCreatedMessageQueueEvent(
        UUID.randomUUID().toString(),
        bidRequest.getBidRequestId(),
        bidRequest.getAuctionId(),
        bidRequest.getMemberId(),
        bidRequest.getBidPrice(),
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
    return EventType.BID_REQUEST_CREATED;
  }
}
