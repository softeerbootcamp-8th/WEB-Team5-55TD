package com.ootd.pickup.global.event.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionalEventPublisherTest {

  private final ApplicationEventPublisher applicationEventPublisher =
      mock(ApplicationEventPublisher.class);
  private final TransactionalEventPublisher eventPublisher =
      new TransactionalEventPublisher(applicationEventPublisher);
  private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(TransactionalEventPublisher.class);

  @BeforeEach
  void setUp() {
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
    logAppender.stop();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void 트랜잭션_안에서_발행하면_스프링_이벤트로_넘긴다() {
    // given
    TransactionSynchronizationManager.initSynchronization();
    NotificationEvent event = testEvent();

    // when
    eventPublisher.publish(event);

    // then
    then(applicationEventPublisher).should().publishEvent(event);
  }

  @Test
  void 트랜잭션_안에서_발행하면_경고를_남기지_않는다() {
    // given
    TransactionSynchronizationManager.initSynchronization();

    // when
    eventPublisher.publish(testEvent());

    // then
    assertThat(warnMessages()).isEmpty();
  }

  @Test
  void 트랜잭션_없이_발행해도_이벤트를_넘긴다() {
    // given — 커밋될 것이 없어 롤백된 값이 나갈 위험도 없으므로 발행을 막지 않는다
    NotificationEvent event = testEvent();

    // when
    eventPublisher.publish(event);

    // then
    then(applicationEventPublisher).should().publishEvent(event);
  }

  @Test
  void 트랜잭션_없이_발행하면_경고를_남긴다() {
    // given
    NotificationEvent event = testEvent();

    // when
    eventPublisher.publish(event);

    // then
    assertThat(warnMessages())
        .singleElement()
        .asString()
        .contains("BID_REQUEST_SUCCEEDED", "aggregateId=1", "eventId=event-id");
  }

  private List<String> warnMessages() {
    return logAppender.list.stream()
        .filter(loggingEvent -> loggingEvent.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
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
