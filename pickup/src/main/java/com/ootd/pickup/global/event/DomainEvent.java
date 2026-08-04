package com.ootd.pickup.global.event;

import java.time.LocalDateTime;

/**
 * 도메인에서 발생한 사건을 표현하는 공통 계약.
 *
 * <p>모든 이벤트는 {@link UnicastEvent}(단일 처리)와 {@link BroadcastEvent}(전체 전파) 중 하나를 골라야 한다. 이 인터페이스를 직접
 * 구현할 수 없도록 {@code sealed}로 막아, 이벤트를 정의할 때 처리 다중도를 반드시 결정하게 한다.
 *
 * <p>구현체는 {@code record}로 작성한다. 소비자는 다른 프로세스에서, 트랜잭션 밖에서 실행되므로 엔티티를 담으면 지연 로딩이 실패한다. 식별자와 원시값만 담는다.
 *
 * <p>{@link #eventId()}와 {@link #occurredAt()}은 호출마다 새 값이 생기면 안 되므로 default로 둘 수 없다. record 컴포넌트로
 * 선언하고 정적 팩토리에서 채운다.
 */
public sealed interface DomainEvent permits UnicastEvent, BroadcastEvent {

  /**
   * 이벤트 고유 식별자.
   *
   * <p>{@link UnicastEvent}는 처리에 실패한 메시지가 다시 전달되므로 같은 핸들러가 같은 이벤트를 두 번 받을 수 있다. 소비자가 이 값으로 중복을
   * 걸러낸다.
   */
  String eventId();

  /** 사건이 발생한 시각. 처리 시각과 구분된다. */
  LocalDateTime occurredAt();

  /**
   * 직렬화 타입 키. 수신 측이 어떤 타입으로 역직렬화할지 판단하는 근거다.
   *
   * <p>기본값은 구현 타입의 단순 이름이다. 클래스명을 바꾸면 큐에 남아 있던 이벤트나 아직 배포되지 않은 소비자가 이름을 찾지 못하므로, 이름을 변경할 때는 이 메서드를
   * 재정의해 기존 값을 고정한다.
   */
  default String eventName() {
    return getClass().getSimpleName();
  }

  /**
   * 전달 경로를 가르는 키. 순서를 지켜야 하는 단위(예: {@code auctionId})를 반환한다.
   *
   * <p>계열마다 쓰이는 곳이 다르다.
   *
   * <ul>
   *   <li>{@link UnicastEvent} — SQS FIFO 큐의 {@code MessageGroupId}. 같은 그룹 안에서만 순서가 보장된다.
   *   <li>{@link BroadcastEvent} — Redis Pub/Sub 채널 이름 구성 요소. 인스턴스가 필요한 경매만 골라 구독할 수 있다.
   * </ul>
   */
  String routingKey();
}
