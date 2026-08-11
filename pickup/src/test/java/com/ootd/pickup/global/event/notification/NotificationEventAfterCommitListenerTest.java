package com.ootd.pickup.global.event.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link EventPublisher}에 넘긴 알림이 언제 전송 계층까지 도달하는지 확인한다.
 *
 * <p>진입점부터 {@link NotificationEventSender}까지 실제 배선을 그대로 태운다. 커밋 시점을 맞추는 책임이 도메인이 아니라 이 경로에 있기 때문이다.
 *
 * <p>알림 실행기는 호출 스레드에서 바로 돌도록 바꾼다. 실제 실행기는 별도 스레드로 넘겨서, 커밋 직후에 단언하면 아직 전송 전일 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationEventAfterCommitListenerTest {

  @Autowired private EventPublisher eventPublisher;

  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private NotificationEventSender notificationEventSender;

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
          eventPublisher.publish(event);
          then(notificationEventSender).shouldHaveNoInteractions();
          then(notificationEventExecutor).shouldHaveNoInteractions();
        });

    then(notificationEventExecutor).should().execute(any(Runnable.class));
    then(notificationEventSender).should().send(event);
  }

  @Test
  void 트랜잭션이_롤백되면_알림을_발행하지_않는다() {
    TestNotificationEvent event = testEvent();

    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publish(event);
          status.setRollbackOnly();
        });

    then(notificationEventExecutor).shouldHaveNoInteractions();
    then(notificationEventSender).shouldHaveNoInteractions();
  }

  @Test
  void 트랜잭션_없이_발행하면_기다리지_않고_전송한다() {
    TestNotificationEvent event = testEvent();

    eventPublisher.publish(event);

    then(notificationEventSender).should().send(event);
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
    public EventType eventType() {
      return EventType.AUCTION_BID_UPDATED;
    }
  }
}
