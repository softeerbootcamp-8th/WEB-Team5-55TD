package com.ootd.pickup.global.event;

import java.time.LocalDateTime;

/**
 * 도메인에서 발생한 사건을 표현하는 공통 계약.
 *
 * <p>구현체는 {@code record}로 작성한다. 소비자는 다른 프로세스에서, 트랜잭션 밖에서 실행되므로 엔티티를 담으면 지연 로딩이 실패한다. 식별자와 원시값만 담는다.
 *
 * <p>{@link #eventId()}와 {@link #occurredAt()}은 호출마다 새 값이 생기면 안 되므로 default로 둘 수 없다. record 컴포넌트로
 * 선언하고 정적 팩토리에서 채운다.
 */
public interface DomainEvent {

  /**
   * 이벤트 고유 식별자.
   *
   * <p>전송 계층이 at-least-once이므로 같은 이벤트가 두 번 도달할 수 있다. 소비자가 이 값으로 중복을 걸러낸다.
   */
  String eventId();

  /** 사건이 발생한 시각. 처리 시각과 구분된다. */
  LocalDateTime occurredAt();

  /**
   * 직렬화 타입 키. 수신 측이 어떤 타입으로 역직렬화할지 판단하는 근거다.
   *
   * <p>기본값은 구현 타입의 단순 이름이다. 클래스명을 바꾸면 브로커에 남아 있던 이벤트나 아직 배포되지 않은 소비자가 이름을 찾지 못하므로, 이름을 변경할 때는 이
   * 메서드를 재정의해 기존 값을 고정한다.
   */
  default String eventName() {
    return getClass().getSimpleName();
  }

  /**
   * 브로커 파티션·스트림 키. 같은 키의 이벤트는 발행 순서대로 전달된다.
   * 브로커의 이벤트 전달 순서 보장을 위한 키를 반환한다.
   */
  String routingKey();
}
