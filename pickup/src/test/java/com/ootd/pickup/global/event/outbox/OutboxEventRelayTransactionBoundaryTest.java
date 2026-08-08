package com.ootd.pickup.global.event.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 큐 전송이 DB 트랜잭션 밖에서 일어나는지 확인한다.
 *
 * <p>전송은 외부 통신이라 응답이 늦을 수 있다. 트랜잭션 안에서 보내면 그 시간 내내 DB 커넥션을 붙잡아 요청 처리 쪽 커넥션이 고갈된다. 한 주기에 최대 100건을
 * 보내므로 지연이 누적된다.
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다. 테스트가 트랜잭션을 열면 전송 시점에도 그 트랜잭션이 살아 있어, 검증하려는 것과 테스트가 만든
 * 트랜잭션을 구분할 수 없다.
 */
@SpringBootTest(properties = "scheduler.outbox.enabled=true")
@ActiveProfiles("test")
class OutboxEventRelayTransactionBoundaryTest {

  @Autowired private OutboxEventScheduler outboxEventScheduler;

  @Autowired private OutboxEventJpaRepository outboxEventJpaRepository;

  @Autowired private EventProducer eventProducer;

  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private MessageQueueSender messageQueueSender;

  private record TestEvent(String eventId, Long auctionId, LocalDateTime occurredAt)
      implements MessageQueueEvent {

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
      return EventType.AUCTION_ENDED;
    }
  }

  @AfterEach
  void 커밋된_적재분을_지운다() {
    transactionTemplate.executeWithoutResult(status -> outboxEventJpaRepository.deleteAll());
  }

  @Test
  void 큐_전송은_트랜잭션_밖에서_일어난다() {
    // given — 전송 시점에 트랜잭션이 살아 있는지 기록한다
    List<Boolean> transactionActiveWhenSent = new ArrayList<>();
    willAnswer(
            invocation -> {
              transactionActiveWhenSent.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            })
        .given(messageQueueSender)
        .send(any());
    appendCommitted();

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(transactionActiveWhenSent).containsExactly(false);
  }

  @Test
  void 발행_완료_표시는_전송_이후에_커밋된다() {
    // given — 전송 시점에는 아직 published=false 여야 한다
    String eventId = appendCommitted();
    List<Boolean> publishedWhenSent = new ArrayList<>();
    willAnswer(
            invocation -> {
              publishedWhenSent.add(readCommittedPublished(eventId));
              return null;
            })
        .given(messageQueueSender)
        .send(any());

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(publishedWhenSent).containsExactly(false);
    assertThat(readCommittedPublished(eventId)).isTrue();
  }

  private String appendCommitted() {
    TestEvent event =
        new TestEvent(UUID.randomUUID().toString(), 1L, LocalDateTime.of(2026, 8, 5, 10, 0));
    transactionTemplate.executeWithoutResult(status -> eventProducer.produce(event));
    return event.eventId();
  }

  private boolean readCommittedPublished(String eventId) {
    return Boolean.TRUE.equals(
        transactionTemplate.execute(
            status -> outboxEventJpaRepository.findById(eventId).orElseThrow().isPublished()));
  }
}
