package com.ootd.pickup.global.event;

/**
 * 알림 이벤트 발행 진입점.
 *
 * <p>도메인 서비스는 이 인터페이스만 알고 커밋 시점도 Redis도 모른다. 구현과 전송 계층은 {@code global.event.notification} 아래에 있고,
 * 트랜잭션 커밋 이후에 내보내는 것까지 그쪽이 책임진다 — 호출자가 {@code afterCommit} 훅을 직접 걸지 않는다.
 *
 * <p>발행 실패는 호출자에게 전파되지 않는다.
 *
 * <p>트랜잭션 없이 호출하면 즉시 발행된다. 발행 시점이 호출 지점 그대로가 되므로, DB 쓰기가 있다면 쓰기 이후에 호출해야 한다.
 *
 * <p>{@link MessageQueueEvent}는 이 메서드에 넘길 수 없다. 파라미터 타입이 갈라져 있어 경매 종료를 실수로 유실 가능한 경로로 보내면 컴파일되지
 * 않는다.
 */
public interface EventPublisher {

  /**
   * 알림 이벤트를 커밋 이후에 발행한다.
   *
   * @param event 발행할 알림 이벤트
   */
  void publish(NotificationEvent event);
}
