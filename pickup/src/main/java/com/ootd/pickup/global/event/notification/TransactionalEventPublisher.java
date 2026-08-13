package com.ootd.pickup.global.event.notification;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 커밋 이후 발행을 보장하는 {@link EventPublisher} 구현체.
 *
 * <p>스프링 이벤트로 넘겨 {@link NotificationEventListener}가 커밋 이후에 받게 한다.
 *
 * <p>트랜잭션이 없으면 리스너가 즉시 실행되고({@code fallbackExecution}) 경고 로그를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalEventPublisher implements EventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  @Override
  public void publish(NotificationEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      log.warn(
          "트랜잭션 없이 알림을 발행합니다 - eventType={}, aggregateId={}, eventId={}",
          event.eventType(),
          event.aggregateId(),
          event.eventId());
    }
    applicationEventPublisher.publishEvent(event);
  }
}
