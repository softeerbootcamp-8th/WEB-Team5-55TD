package com.ootd.pickup.global.event.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationEventListenerTest {

  private final NotificationEventSender notificationEventSender =
      mock(NotificationEventSender.class);
  private final AtomicReference<Runnable> submittedTask = new AtomicReference<>();
  private final Executor executor = submittedTask::set;
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RealtimeNotificationMetrics metrics =
      new RealtimeNotificationMetrics(meterRegistry);
  private final NotificationEventListener listener =
      new NotificationEventListener(notificationEventSender, executor, metrics);

  @Test
  void 커밋_리스너는_Redis_발행을_executor에_위임한다() {
    NotificationEvent event = testEvent();

    listener.publish(event);

    then(notificationEventSender).shouldHaveNoInteractions();

    submittedTask.get().run();

    then(notificationEventSender).should().send(event);
  }

  @Test
  void executor가_작업을_거부해도_호출자에게_예외를_전파하지_않는다() {
    Executor rejectingExecutor =
        command -> {
          throw new RejectedExecutionException("queue full");
        };
    NotificationEventListener rejectingListener =
        new NotificationEventListener(notificationEventSender, rejectingExecutor, metrics);

    assertThatCode(() -> rejectingListener.publish(testEvent())).doesNotThrowAnyException();
    then(notificationEventSender).shouldHaveNoInteractions();
    assertThat(
            meterRegistry
                .get("pickup.redis.notification.publish")
                .tags("outcome", "rejected", "event_type", "BID_REQUEST_SUCCEEDED")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void 비동기_Redis_발행_실패를_호출자에게_전파하지_않는다() {
    NotificationEvent event = testEvent();
    willThrow(new IllegalStateException("redis unavailable"))
        .given(notificationEventSender)
        .send(event);

    listener.publish(event);

    assertThatCode(() -> submittedTask.get().run()).doesNotThrowAnyException();
  }

  private NotificationEvent testEvent() {
    return new TestNotificationEvent("event-id", 1L, LocalDateTime.now());
  }

  private record TestNotificationEvent(String eventId, Long auctionId, LocalDateTime occurredAt)
      implements NotificationEvent {

    @Override
    public AggregateType aggregateType() {
      return AggregateType.AUCTION;
    }

    @Override
    public Long aggregateId() {
      return auctionId;
    }

    @Override
    public EventType eventType() {
      return EventType.BID_REQUEST_SUCCEEDED;
    }
  }
}
