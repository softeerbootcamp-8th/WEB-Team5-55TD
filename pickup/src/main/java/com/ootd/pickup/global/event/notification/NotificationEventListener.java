package com.ootd.pickup.global.event.notification;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link EventPublisher}가 넘긴 알림을 커밋 이후 {@link NotificationEventSender}로 보내는 다리.
 *
 * <p>전송은 별도 실행기로 넘긴다. 실행 큐가 가득 차면 그 알림은 발행하지 않고 로그와 지표로만 남긴다.
 *
 * <p>{@code fallbackExecution = true}라 트랜잭션이 없으면 이 리스너가 즉시 실행된다.
 */
@Slf4j
@Component
public class NotificationEventListener {

  private final NotificationEventSender notificationEventSender;
  private final Executor notificationEventExecutor;
  private final RealtimeNotificationMetrics metrics;

  public NotificationEventListener(
      NotificationEventSender notificationEventSender,
      @Qualifier("notificationEventExecutor") Executor notificationEventExecutor,
      RealtimeNotificationMetrics metrics) {
    this.notificationEventSender = notificationEventSender;
    this.notificationEventExecutor = notificationEventExecutor;
    this.metrics = metrics;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
      notificationEventSender.send(event);
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
