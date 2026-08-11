package com.ootd.pickup.member.event;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import com.ootd.pickup.member.domain.Member;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 회원가입 사건 — 가입 후 포인트 계좌 생성용.
 *
 * <p>포인트 계좌가 만들어지지 않으면 이후 어떤 입찰·정산도 할 수 없으므로 {@link MessageQueueEvent}로 분류한다. Outbox에 먼저 저장되고 별도
 * Relay가 SQS FIFO 큐로 옮긴다.
 */
public record MemberRegisteredMessageQueueEvent(
    String eventId, Long memberId, LocalDateTime occurredAt) implements MessageQueueEvent {

  public static MemberRegisteredMessageQueueEvent fromEntity(Member member) {
    return new MemberRegisteredMessageQueueEvent(
        UUID.randomUUID().toString(), member.getMemberId(), LocalDateTime.now(ZoneOffset.UTC));
  }

  @Override
  public AggregateType aggregateType() {
    return AggregateType.MEMBER;
  }

  @Override
  public Long aggregateId() {
    return memberId;
  }

  @Override
  public EventType eventType() {
    return EventType.MEMBER_REGISTERED;
  }
}
