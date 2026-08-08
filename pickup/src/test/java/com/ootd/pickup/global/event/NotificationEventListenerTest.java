package com.ootd.pickup.global.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationEventListenerTest {

  private final EventPublisher eventPublisher = mock(EventPublisher.class);
  private final AtomicReference<Runnable> submittedTask = new AtomicReference<>();
  private final Executor executor = submittedTask::set;
  private final NotificationEventListener listener =
      new NotificationEventListener(eventPublisher, executor);

  @Test
  void 커밋_리스너는_Redis_발행을_executor에_위임한다() {
    NotificationEvent event = testEvent();

    listener.publish(event);

    then(eventPublisher).shouldHaveNoInteractions();

    submittedTask.get().run();

    then(eventPublisher).should().publish(event);
  }

  @Test
  void executor가_작업을_거부해도_호출자에게_예외를_전파하지_않는다() {
    Executor rejectingExecutor =
        command -> {
          throw new RejectedExecutionException("queue full");
        };
    NotificationEventListener rejectingListener =
        new NotificationEventListener(eventPublisher, rejectingExecutor);

    assertThatCode(() -> rejectingListener.publish(testEvent())).doesNotThrowAnyException();
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void 비동기_Redis_발행_실패를_호출자에게_전파하지_않는다() {
    NotificationEvent event = testEvent();
    willThrow(new IllegalStateException("redis unavailable")).given(eventPublisher).publish(event);

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
      return EventType.AUCTION_BID_UPDATED;
    }
  }
}
