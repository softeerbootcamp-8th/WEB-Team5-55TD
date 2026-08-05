package com.ootd.pickup.global.event;

import java.time.LocalDateTime;

/**
 * 도메인에서 발생한 사건을 표현하는 공통 계약.
 *
 * <p>모든 이벤트는 {@link MessageQueueEvent}(한 소비자만 처리)와 {@link NotificationEvent}(구독한 전부가 처리) 중 하나를 골라야
 * 한다. 이 인터페이스를 직접 구현할 수 없도록 {@code sealed}로 막아, 이벤트를 정의할 때 처리 다중도를 반드시 결정하게 한다.
 *
 * <p>메서드는 {@code outbox_event} 테이블 컬럼에 대응한다.
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
 * <p>{@code published} 컬럼은 릴레이의 전송 상태이지 사건의 성질이 아니므로 이 계약에 두지 않는다.
 *
 * <p>테이블에 저장되는 것은 {@link MessageQueueEvent}뿐이다. {@link NotificationEvent}는 Outbox를 거치지 않고 같은 값을 채널
 * 이름과 메시지 구성에 쓴다.
 *
 * <p>구현체는 {@code record}로 작성한다. 소비자는 다른 프로세스에서, 트랜잭션 밖에서 실행되므로 엔티티를 담으면 지연 로딩이 실패한다. 식별자와 원시값만 담는다.
 *
 * <p>{@link #eventId()}와 {@link #occurredAt()}은 호출마다 새 값이 생기면 안 되므로 default로 둘 수 없다. record 컴포넌트로
 * 선언하고 정적 팩토리에서 채운다.
 */
public sealed interface DomainEvent permits MessageQueueEvent, NotificationEvent {

  /**
   * 이벤트 고유 식별자. {@code outbox_event.id}에 저장된다.
   *
   * <p>{@code UUID.randomUUID().toString()}이 하이픈 포함 36자라 {@code VARCHAR(36)}과 정확히 맞는다. 다른 형식을 쓰면 컬럼
   * 길이를 함께 조정해야 한다.
   *
   * <p>{@link MessageQueueEvent}는 처리에 실패한 메시지가 다시 전달되므로 같은 핸들러가 같은 이벤트를 두 번 받을 수 있다. 소비자가 이 값으로 중복을
   * 걸러낸다.
   */
  String eventId();

  /**
   * 사건이 속한 애그리거트 종류. {@link #aggregateId()}와 짝을 이룬다.
   *
   * <p>{@code aggregateId}만으로는 1번이 경매인지 회원인지 알 수 없다. 두 값을 함께 써야 사건의 대상이 특정된다.
   *
   * <p>애그리거트는 이벤트 이름과 다를 수 있다. 순서와 일관성을 지켜야 하는 경계를 가리키기 때문이다. 예를 들어 입찰로 발생하는 이벤트도 현재가 순서를 경매 단위로
   * 지켜야 하므로 {@link AggregateType#AUCTION}과 경매 식별자를 반환한다.
   */
  AggregateType aggregateType();

  /**
   * 사건이 속한 애그리거트 식별자. {@code outbox_event.aggregate_id}에 저장된다. 경매에서 일어난 사건이면 {@code auctionId}다.
   *
   * <p>{@link #aggregateType()}과 함께 순서를 지켜야 하는 단위를 정한다. 계열마다 쓰이는 곳이 다르다.
   *
   * <ul>
   *   <li>{@link MessageQueueEvent} — SQS FIFO 큐의 {@code MessageGroupId}를 {@code AUCTION:1024}처럼 두
   *       값을 묶어 만든다. 같은 애그리거트 안에서만 순서가 보장되므로, 경매 시작과 종료가 같은 그룹에 들어가야 한다.
   *   <li>{@link NotificationEvent} — Redis Pub/Sub 채널 이름 구성 요소. 인스턴스가 필요한 애그리거트만 골라 구독할 수 있다.
   * </ul>
   */
  Long aggregateId();

  /**
   * 직렬화 타입 키. {@code outbox_event.event_type}에 저장되고, 수신 측이 어떤 타입으로 역직렬화할지 판단하는 근거가 된다.
   *
   * <p>{@link EventType} 중 하나를 반드시 반환해야 한다(default 없음). 구현 타입의 클래스명에 기대면, 클래스명이 바뀌었을 때 큐에 남아 있던
   * 이벤트나 아직 배포되지 않은 소비자가 조용히 값을 못 찾는 문제가 생긴다. enum으로 제약해두면 새 타입을 추가하거나 이름을 바꿀 때 이 값을 참조하는 발행/소비 양쪽
   * 코드가 컴파일 시점에 함께 갱신된다.
   */
  EventType eventType();

  /** 사건이 발생한 시각. {@code outbox_event.created_at}에 저장된다. 처리 시각과 구분된다. */
  LocalDateTime occurredAt();
}
