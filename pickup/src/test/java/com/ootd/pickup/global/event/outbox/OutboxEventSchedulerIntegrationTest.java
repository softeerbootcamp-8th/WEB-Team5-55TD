package com.ootd.pickup.global.event.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 릴레이는 기본적으로 꺼져 있으므로 이 테스트에서만 켠다. */
@SpringBootTest(properties = "scheduler.outbox.enabled=true")
@ActiveProfiles("test")
@Transactional
class OutboxEventSchedulerIntegrationTest {

  @Autowired private OutboxEventScheduler outboxEventScheduler;

  @Autowired private OutboxEventJpaRepository outboxEventJpaRepository;

  @Autowired private EventProducer eventProducer;

  @Autowired private EntityManager entityManager;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private MessageQueueSender messageQueueSender;

  private record TestEvent(
      String eventId, Long auctionId, Long winningPrice, LocalDateTime occurredAt)
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

  @BeforeEach
  void 이전_테스트가_남긴_적재분을_비운다() {
    outboxEventJpaRepository.deleteAll();
    entityManager.flush();
  }

  @Test
  void 미발행_이벤트를_발행하고_발행_완료로_표시한다() {
    // given
    OutboxEventEntity appended = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0)));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    then(messageQueueSender).should().send(any(MessageQueueEvent.class));
    assertThat(findById(appended.getId()).isPublished()).isTrue();
  }

  @Test
  void 발행되는_이벤트는_적재된_컬럼_값을_그대로_담는다() {
    // given
    TestEvent event = testEvent(1024L, LocalDateTime.of(2026, 8, 5, 10, 0));
    OutboxEventEntity appended = append(event);

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    RelayedOutboxEvent relayed = capturedRelayedEvent();
    assertThat(relayed.eventId()).isEqualTo(appended.getId());
    assertThat(relayed.aggregateType()).isEqualTo(AggregateType.AUCTION);
    assertThat(relayed.aggregateId()).isEqualTo(1024L);
    assertThat(relayed.eventType()).isEqualTo(EventType.AUCTION_ENDED);
    assertThat(relayed.occurredAt()).isEqualTo(event.occurredAt());
  }

  @Test
  void 발행되는_payload는_적재된_원문과_완전히_같다() {
    // given — 릴레이가 역직렬화 후 재직렬화하면 값이 달라질 수 있다. 원문을 그대로 옮기는지 확인한다
    TestEvent event = testEvent(1024L, LocalDateTime.of(2026, 8, 5, 10, 0));
    append(event);

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(capturedRelayedEvent().payload()).isEqualTo(objectMapper.writeValueAsString(event));
  }

  @Test
  void 이미_발행된_이벤트는_다시_발행하지_않는다() {
    // given
    OutboxEventEntity appended = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0)));
    outboxEventJpaRepository.updatePublishedByIdIn(List.of(appended.getId()));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    then(messageQueueSender).shouldHaveNoInteractions();
  }

  @Test
  void 오래된_이벤트부터_순서대로_발행한다() {
    // given
    append(testEvent(3L, LocalDateTime.of(2026, 8, 5, 10, 0, 3)));
    append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 1)));
    append(testEvent(2L, LocalDateTime.of(2026, 8, 5, 10, 0, 2)));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    ArgumentCaptor<MessageQueueEvent> captor = ArgumentCaptor.forClass(MessageQueueEvent.class);
    then(messageQueueSender).should(times(3)).send(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(MessageQueueEvent::aggregateId)
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  void 전송에_실패한_이벤트는_발행_완료로_표시하지_않는다() {
    // given
    OutboxEventEntity appended = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0)));
    willThrow(new IllegalStateException("큐 장애")).given(messageQueueSender).send(any());

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(findById(appended.getId()).isPublished()).isFalse();
  }

  @Test
  void 다른_애그리거트의_실패는_영향을_주지_않는다() {
    // given — 경매가 다르면 FIFO 그룹도 달라 순서를 함께 지킬 필요가 없다
    OutboxEventEntity failing = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 1)));
    OutboxEventEntity succeeding = append(testEvent(2L, LocalDateTime.of(2026, 8, 5, 10, 0, 2)));
    willThrow(new IllegalStateException("큐 장애"))
        .willDoNothing()
        .given(messageQueueSender)
        .send(any());

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(findById(failing.getId()).isPublished()).isFalse();
    assertThat(findById(succeeding.getId()).isPublished()).isTrue();
  }

  @Test
  void 같은_애그리거트의_앞선_이벤트가_실패하면_뒤_이벤트를_보내지_않는다() {
    // given — 계속 보내면 뒤 이벤트가 큐에 먼저 들어가고, 다음 주기가 앞 이벤트를 재시도해
    // 같은 FIFO 그룹 안에서 순서가 역전된다
    OutboxEventEntity first = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 1)));
    OutboxEventEntity second = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 2)));
    OutboxEventEntity otherAggregate =
        append(testEvent(2L, LocalDateTime.of(2026, 8, 5, 10, 0, 3)));
    willAnswer(
            invocation -> {
              MessageQueueEvent event = invocation.getArgument(0);
              if (first.getId().equals(event.eventId())) {
                throw new IllegalStateException("큐 장애");
              }
              return null;
            })
        .given(messageQueueSender)
        .send(any());

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    ArgumentCaptor<MessageQueueEvent> captor = ArgumentCaptor.forClass(MessageQueueEvent.class);
    then(messageQueueSender).should(atLeast(0)).send(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(MessageQueueEvent::eventId)
        .doesNotContain(second.getId());

    assertThat(findById(first.getId()).isPublished()).isFalse();
    assertThat(findById(second.getId()).isPublished()).isFalse();
    assertThat(findById(otherAggregate.getId()).isPublished()).isTrue();
  }

  @Test
  void 발행_대상이_없으면_아무것도_하지_않는다() {
    // given — 적재분이 비어 있다

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    then(messageQueueSender).shouldHaveNoInteractions();
  }

  private RelayedOutboxEvent capturedRelayedEvent() {
    ArgumentCaptor<MessageQueueEvent> captor = ArgumentCaptor.forClass(MessageQueueEvent.class);
    then(messageQueueSender).should().send(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RelayedOutboxEvent.class);
    return (RelayedOutboxEvent) captor.getValue();
  }

  private TestEvent testEvent(Long auctionId, LocalDateTime occurredAt) {
    return new TestEvent(UUID.randomUUID().toString(), auctionId, 50000L, occurredAt);
  }

  private OutboxEventEntity append(MessageQueueEvent event) {
    eventProducer.produce(event);
    entityManager.flush();
    return outboxEventJpaRepository.findById(event.eventId()).orElseThrow();
  }

  private OutboxEventEntity findById(String id) {
    entityManager.flush();
    entityManager.clear();
    return outboxEventJpaRepository.findById(id).orElseThrow();
  }
}
