package com.ootd.pickup.global.event;

/**
 * 도메인 이벤트 수신 진입점. {@link EventPublisher}의 소비 쪽 대칭이다.
 *
 * <p>전송 어댑터가 브로커 메시지를 역직렬화한 뒤 이 메서드를 호출한다. 구현체는 {@link EventHandler#eventType()} 매칭으로 소비자를 골라내고,
 * 소비자 단위로 예외를 격리해 한 소비자의 실패가 나머지를 막지 않도록 한다.
 *
 * <p>이 계약을 두는 이유는 전송 어댑터마다 라우팅 방식을 각자 발명하지 않게 하기 위함이다.
 */
public interface EventDispatcher {

  void dispatch(DomainEvent event);
}
