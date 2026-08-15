package com.ootd.pickup.global.event.notification;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import com.ootd.pickup.global.observability.TraceContextCarrier;
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
    // 실행기로 넘기기 전, 지금 스레드(커밋 직후 - 원래 요청/소비자 트레이스가 아직 활성 상태)의 트레이스를 떠 둔다.
    // 실행기 스레드에서는 이 값이 없으면 어느 트레이스에 이어 붙일지 알 방법이 없다.
    String traceParent = TraceContextCarrier.captureCurrentTraceParent();
    try {
      notificationEventExecutor.execute(
          () ->
              TraceContextCarrier.runWithExtractedContext(
                  traceParent,
                  "NotificationEventListener.publishAsync",
                  () -> publishAsync(event)));
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
      log.debug(
          "알림 이벤트를 발행했습니다 - eventType={}, aggregateId={}, eventId={}",
          event.eventType(),
          event.aggregateId(),
          event.eventId());
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
