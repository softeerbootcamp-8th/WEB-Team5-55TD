package com.ootd.pickup.global.event;

/**
 * 도메인 이벤트 발행 진입점.
 *
 * <p>도메인 서비스가 의존하는 유일한 이벤트 인프라 타입이다. 전달 수단(Redis, Kafka)과 커밋–발행 원자성 보장 방식(직접 전송, Outbox)은 구현체가
 * 결정하므로 이 시그니처는 어느 쪽을 선택해도 바뀌지 않는다.
 */
public interface EventPublisher {

  void publish(DomainEvent event);
}
