package com.ootd.pickup.global.event;

import static org.mockito.BDDMockito.then;

import java.time.LocalDateTime;
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

  @Test
  void 트랜잭션이_커밋된_뒤에_알림을_발행한다() {
    TestNotificationEvent event = testEvent();

    transactionTemplate.executeWithoutResult(
        status -> {
          applicationEventPublisher.publishEvent(event);
          then(eventPublisher).shouldHaveNoInteractions();
        });

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
