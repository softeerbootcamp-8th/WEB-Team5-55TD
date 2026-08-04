package com.ootd.pickup.global.event;

/**
 * 단일 처리 이벤트 발행 진입점.
 *
 * <p>도메인 서비스는 이 인터페이스만 알고 Outbox 테이블도 SQS도 모른다. 구현체가 이벤트를 Outbox에 저장하고, 릴레이가 SQS FIFO 큐로 옮긴다.
 *
 * <p>{@link BroadcastEvent}는 이 메서드에 넘길 수 없다. 파라미터 타입이 갈라져 있어 경매 종료를 실수로 Redis 쪽으로 보내면 컴파일되지 않는다.
 */
public interface EventProducer {

  void produce(UnicastEvent event);
}
