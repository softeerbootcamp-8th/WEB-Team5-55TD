package com.ootd.pickup.global.event;

import java.time.LocalDateTime;

/**
 * 도메인에서 발생한 사건을 표현하는 공통 계약.
 *
 * <p>{@code sealed}로 막아 모든 이벤트가 {@link MessageQueueEvent}(한 소비자만 처리)와 {@link NotificationEvent}(구독한
 * 전부가 처리) 중 하나를 반드시 고르게 한다.
 *
 * <p>메서드는 {@code outbox_event} 테이블 컬럼에 대응한다. {@code published}는 사건의 성질이 아니라 릴레이의 전송 상태라 빠져 있다.
 *
 * <ul>
 *   <li>{@code id VARCHAR(36)} — {@link #eventId()}
 *   <li>{@code aggregate_type} — {@link #aggregateType()}
 *   <li>{@code aggregate_id BIGINT} — {@link #aggregateId()}
 *   <li>{@code event_type VARCHAR(50)} — {@link #eventType()}
 *   <li>{@code created_at DATETIME} — {@link #occurredAt()}
 *   <li>{@code payload JSON} — 구현 record 전체를 직렬화한 값
 * </ul>
 *
 * <p>구현체는 {@code record}로 쓰고 식별자와 원시값만 담는다. 소비자는 다른 프로세스에서 트랜잭션 밖에 실행되므로 엔티티를 담으면 지연 로딩이 실패한다.
 *
 * <p>{@link #eventId()}와 {@link #occurredAt()}은 record 컴포넌트로 선언한다. default 메서드로 두면 호출마다 새 값이 나온다.
 */
public sealed interface DomainEvent permits MessageQueueEvent, NotificationEvent {

  /**
   * 이벤트 고유 식별자. 소비자가 이 값으로 중복 처리를 걸러낸다.
   *
   * <p>{@code UUID.randomUUID().toString()}이 하이픈 포함 36자라 {@code VARCHAR(36)}과 맞는다. 다른 형식을 쓰면 컬럼 길이도
   * 함께 조정해야 한다.
   */
  String eventId();

  /**
   * 사건이 속한 애그리거트 종류. {@link #aggregateId()}와 짝을 이뤄야 대상이 특정된다.
   *
   * <p>이벤트 이름과 다를 수 있다. 순서와 일관성을 지켜야 하는 경계를 가리키기 때문이다. 입찰로 발생하는 이벤트도 현재가 순서를 경매 단위로 지켜야 하므로 {@link
   * AggregateType#AUCTION}을 반환한다.
   */
  AggregateType aggregateType();

  /**
   * 사건이 속한 애그리거트 식별자. 경매에서 일어난 사건이면 {@code auctionId}다.
   *
   * <p>{@link #aggregateType()}과 함께 순서를 지켜야 하는 단위를 정한다. 메시지 큐 계열은 이 단위로 FIFO 그룹을, 알림 계열은 Pub/Sub 채널
   * 이름을 만든다.
   */
  Long aggregateId();

  /**
   * 사건의 종류. 수신 측이 payload를 어떤 타입으로 되돌릴지 정하는 근거다.
   *
   * <p>기본 구현을 두지 않아 구현체가 {@link EventType} 중 하나를 반드시 고른다. 저장 값이 클래스 이름에서 도출되면 클래스명을 바꾸는 순간 큐에 남아 있던
   * 메시지를 되돌릴 수 없다.
   */
  EventType eventType();

  /** 사건이 발생한 시각. 처리 시각과 구분된다. */
  LocalDateTime occurredAt();
}
