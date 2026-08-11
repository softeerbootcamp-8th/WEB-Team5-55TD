package com.ootd.pickup.global.event;

import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class NotificationEventListener {

  private final EventPublisher eventPublisher;
  private final Executor notificationEventExecutor;
  private final RealtimeNotificationMetrics metrics;

  public NotificationEventListener(
      EventPublisher eventPublisher,
      @Qualifier("notificationEventExecutor") Executor notificationEventExecutor,
      RealtimeNotificationMetrics metrics) {
    this.eventPublisher = eventPublisher;
    this.notificationEventExecutor = notificationEventExecutor;
    this.metrics = metrics;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(NotificationEvent event) {
    try {
      notificationEventExecutor.execute(() -> publishAsync(event));
    } catch (RejectedExecutionException exception) {
      metrics.recordRedisPublishRejected(event.eventType());
      log.warn(
          "알림 이벤트 실행 큐가 가득 차 발행하지 못했습니다. eventType={}, aggregateId={}, eventId={}",
          event.eventType(),
          event.aggregateId(),
          event.eventId(),
          exception);
    }
  }

  private void publishAsync(NotificationEvent event) {
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
