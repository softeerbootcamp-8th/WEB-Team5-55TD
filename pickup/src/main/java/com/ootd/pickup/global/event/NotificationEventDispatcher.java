package com.ootd.pickup.global.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
