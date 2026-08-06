package com.ootd.pickup.global.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final EventPublisher eventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(NotificationEvent event) {
    try {
      eventPublisher.publish(event);
    } catch (RuntimeException exception) {
      log.warn(
          "알림 이벤트 발행에 실패했습니다. eventType={}, aggregateId={}, eventId={}",
          event.eventType(),
          event.aggregateId(),
          event.eventId(),
          exception);
    }
  }
}
