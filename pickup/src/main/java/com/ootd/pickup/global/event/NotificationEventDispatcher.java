package com.ootd.pickup.global.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 알림 이벤트를 받을 {@link EventHandler}를 찾아 넘기는 디스패처.
 *
 * <p>{@link NotificationEvent} 계열 전용이다 — {@link EventHandler}의 클래스 문서가 명시하듯 이 계열은 핸들러별 예외를 격리해 다른
 * 핸들러가 계속 실행되게 한다. {@link MessageQueueEvent}는 예외를 던져야 메시지가 재전달되므로(재전달 시 이미 성공한 핸들러도 함께 다시 실행돼 결과가
 * 같아야 하는 전제) 이 격리 정책을 그대로 재사용할 수 없다 — SQS 소비자가 구현되더라도 이 클래스를 그대로 가져다 쓰면 안 된다.
 *
 * <p>전송 계층(Redis Pub/Sub 등)에 특화된 부분이 없어 {@code global.event.redis} 밖의 이 패키지에 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventDispatcher {

  private final List<EventHandler<? extends DomainEvent>> eventHandlers;

  public void dispatch(NotificationEvent event) {
    List<EventHandler<? extends DomainEvent>> matchingHandlers =
        eventHandlers.stream()
            .filter(handler -> handler.eventClass().equals(event.getClass()))
            .toList();
    if (matchingHandlers.isEmpty()) {
      log.warn(
          "알림 이벤트 처리기가 없습니다 - eventType={}, aggregateId={}",
          event.eventType(),
          event.aggregateId());
      return;
    }
    matchingHandlers.forEach(handler -> handle(handler, event));
  }

  private <E extends DomainEvent> void handle(
      EventHandler<E> eventHandler, NotificationEvent event) {
    try {
      eventHandler.handle(eventHandler.eventClass().cast(event));
      log.debug(
          "알림 이벤트 처리를 완료했습니다 - eventType={}, aggregateId={}, handler={}",
          event.eventType(),
          event.aggregateId(),
          eventHandler.getClass().getSimpleName());
    } catch (RuntimeException exception) {
      log.warn(
          "알림 이벤트 처리에 실패했습니다 - eventType={}, aggregateId={}, handler={}",
          event.eventType(),
          event.aggregateId(),
          eventHandler.getClass().getSimpleName(),
          exception);
    }
  }
}
