package com.ootd.pickup.global.event.messagequeue.outbox;

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
 * @param traceParent 적재 시점의 W3C traceparent. 없으면(에이전트 미부착 등) {@code null}
 */
public record RelayedOutboxEvent(
    String eventId,
    AggregateType aggregateType,
    Long aggregateId,
    EventType eventType,
    LocalDateTime occurredAt,
    String payload,
    String traceParent)
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
        outboxEvent.getPayload(),
        outboxEvent.getTraceParent());
  }

  /**
   * 순서를 함께 지켜야 하는 단위. FIFO 큐의 {@code MessageGroupId}로 쓰인다.
   *
   * <p>{@code eventType}을 섞으면 안 된다. 같은 경매의 시작과 종료가 다른 그룹으로 갈라져 FIFO 큐를 고른 이유가 사라진다.
   *
   * <p><b>보내는 쪽과 막는 쪽이 반드시 같은 값을 봐야 한다.</b> {@code MessageQueueSender} 구현체는 이 값을 실제 그룹으로 실어 보내고,
   * {@link OutboxEventScheduler}는 전송에 실패한 그룹의 뒤 이벤트를 이 값으로 걸러 낸다. 두 기준이 어긋나면 릴레이가 서로 다른 그룹이라고 판단한 두
   * 이벤트가 큐에서는 같은 그룹에 들어가, 실패한 이벤트를 재시도하는 사이에 뒤 이벤트가 먼저 자리를 잡는다. 그래서 양쪽이 각자 만들지 않고 여기 한 곳에서만 정의한다.
   *
   * @return {@code AUCTION:1024} 형태의 그룹 식별자
   */
  public String messageGroupId() {
    return aggregateType + ":" + aggregateId;
  }
}
