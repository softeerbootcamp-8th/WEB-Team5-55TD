package com.ootd.pickup.global.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class NotificationEventAfterCommitListenerTest {

  @Autowired private ApplicationEventPublisher applicationEventPublisher;

  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private EventPublisher eventPublisher;

  @MockitoBean(name = "notificationEventExecutor")
  private Executor notificationEventExecutor;

  @BeforeEach
  void setUp() {
    willAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .given(notificationEventExecutor)
        .execute(any(Runnable.class));
  }

  @Test
  void 트랜잭션이_커밋된_뒤에_알림을_발행한다() {
    TestNotificationEvent event = testEvent();

    transactionTemplate.executeWithoutResult(
        status -> {
          applicationEventPublisher.publishEvent(event);
          then(eventPublisher).shouldHaveNoInteractions();
          then(notificationEventExecutor).shouldHaveNoInteractions();
        });

    then(notificationEventExecutor).should().execute(any(Runnable.class));
    then(eventPublisher).should().publish(event);
  }

  @Test
  void 트랜잭션이_롤백되면_알림을_발행하지_않는다() {
    TestNotificationEvent event = testEvent();

    transactionTemplate.executeWithoutResult(
        status -> {
          applicationEventPublisher.publishEvent(event);
          status.setRollbackOnly();
        });

    then(notificationEventExecutor).shouldHaveNoInteractions();
    then(eventPublisher).shouldHaveNoInteractions();
  }

  private TestNotificationEvent testEvent() {
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
    public String eventType() {
      return "TEST_NOTIFICATION";
    }
  }
}
