package com.ootd.pickup.global.event;

/**
 * 도메인 이벤트 소비 계약.
 *
 * <p>처리할 이벤트 타입을 {@link #eventType()}으로 선언하면 {@link EventDispatcher}가 해당 타입의 이벤트만 전달한다. 소비자는 자신이
 * 다루는 타입만 보므로 {@code instanceof} 분기와 캐스팅이 필요 없다.
 *
 * <p>하나의 이벤트에 여러 소비자를 붙일 수 있고, 소비자끼리는 서로를 알지 못한다.
 *
 * @param <E> 이 소비자가 처리하는 이벤트 타입
 */
public interface EventHandler<E extends DomainEvent> {

  Class<E> eventType();

  void handle(E event);
}
