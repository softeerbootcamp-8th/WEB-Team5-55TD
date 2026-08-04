package com.ootd.pickup.global.event;

/**
 * 도메인 이벤트 소비 계약. 비즈니스 처리를 담당한다.
 *
 * <p>처리할 이벤트 타입을 {@link #eventType()}으로 선언한다. 큐·채널에서 메시지를 꺼내오는 쪽(SQS 컨슈머, Redis 서브스크라이버)이 이 값을 보고
 * 맞는 핸들러에게만 넘긴다. 핸들러는 자신이 다루는 타입만 보므로 {@code instanceof} 분기와 캐스팅이 필요 없다.
 *
 * <p>이벤트가 SQS로 왔는지 Redis로 왔는지는 알 필요가 없어 양쪽 계열이 이 계약을 공용으로 쓴다.
 *
 * <p>하나의 이벤트에 여러 핸들러를 붙일 수 있고, 핸들러끼리는 서로를 알지 못한다. 다만 실패했을 때 벌어지는 일은 계열마다 다르다.
 *
 * <ul>
 *   <li>{@link UnicastEvent} — 예외를 던지면 메시지가 삭제되지 않고 다시 전달된다. 이미 성공한 핸들러도 함께 다시 실행되므로 여러 번 실행돼도 결과가
 *       같아야 한다. {@link DomainEvent#eventId()}로 중복을 걸러낸다.
 *   <li>{@link BroadcastEvent} — 예외가 격리되어 다른 핸들러는 계속 실행된다. 재전달은 없다.
 * </ul>
 *
 * @param <E> 이 핸들러가 처리하는 이벤트 타입
 */
public interface EventHandler<E extends DomainEvent> {

  Class<E> eventType();

  void handle(E event);
}
