package com.ootd.pickup.global.event;

/**
 * 알림 이벤트 발행 진입점.
 *
 * <p>도메인 서비스는 이 인터페이스만 알고 Redis를 모른다. 구현체가 Redis Pub/Sub 채널로 즉시 보낸다. 유실을 막는 장치가 없는 대신 발행이 가볍다.
 *
 * <p>구현체는 트랜잭션 커밋 이후에 보내야 한다. 커밋 전에 보내면 롤백된 입찰의 현재가가 화면에 뜬다. 유실은 허용되지만 틀린 값을 보내는 것은 허용되지 않는다.
 *
 * <p>{@link MessageQueueEvent}는 이 메서드에 넘길 수 없다. 파라미터 타입이 갈라져 있어 경매 종료를 실수로 유실 가능한 경로로 보내면 컴파일되지
 * 않는다.
 */
public interface EventPublisher {

  void publish(NotificationEvent event);
}
