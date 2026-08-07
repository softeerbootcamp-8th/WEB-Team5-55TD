package com.ootd.pickup.global.event.outbox;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import java.time.LocalDateTime;

/**
 * Outbox 행을 그대로 실어 큐로 넘기는 어댑터.
 *
 * <p>사건을 새로 표현하는 것이 아니라 이미 기록된 사건을 옮기는 껍데기다. {@link MessageQueueEvent}를 구현하는 것은 {@link
 * MessageQueueSender#send}의 파라미터 타입을 맞추기 위해서다.
 *
 * <p>릴레이는 payload 안을 볼 이유가 없다. 전송에 필요한 값이 전부 컬럼에 있고, 본문은 적재할 때 만든 JSON 원문을 그대로 쓴다. 역직렬화한 뒤 다시 직렬화하면
 * {@code event_type}을 자바 타입으로 되돌리는 일이 릴레이에 들어온다. 타입이 필요한 쪽은 소비자다.
 *
 * @param payload 적재 시점에 직렬화된 JSON 원문. 큐 본문에 그대로 쓴다
 */
public record RelayedOutboxEvent(
    String eventId,
    AggregateType aggregateType,
    Long aggregateId,
    EventType eventType,
    LocalDateTime occurredAt,
    String payload)
    implements MessageQueueEvent {

  /**
   * 적재된 행을 전송 대상으로 감싼다. 외부에서는 {@link OutboxEventEntity#toEvent()}를 쓴다.
   *
   * <p>{@code createdAt}을 {@code occurredAt}으로 옮기는 것은 그 컬럼이 사건 발생 시각을 담고 있기 때문이다. 적재 시각이 아니다.
   *
   * @param outboxEvent 발행할 Outbox 행
   * @return 컬럼 값을 그대로 담은 전송 대상
   */
  static RelayedOutboxEvent from(OutboxEventEntity outboxEvent) {
    return new RelayedOutboxEvent(
        outboxEvent.getId(),
        outboxEvent.getAggregateType(),
        outboxEvent.getAggregateId(),
        outboxEvent.getEventType(),
        outboxEvent.getCreatedAt(),
        outboxEvent.getPayload());
  }
}
