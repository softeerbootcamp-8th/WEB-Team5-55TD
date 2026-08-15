package com.ootd.pickup.global.event.messagequeue.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import com.ootd.pickup.global.event.messagequeue.outbox.BatchSendResult.FailedEvent;
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

  @Autowired private OutboxEventRepository outboxEventJpaRepository;

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
    // 기본값: 넘어온 배치 전부를 성공으로 응답한다. 실패를 검증하는 테스트는 개별적으로 스텁을 덮어쓴다.
    given(messageQueueSender.sendBatch(anyList()))
        .willAnswer(invocation -> allSucceeded(invocation.getArgument(0)));
  }

  @Test
  void 미발행_이벤트를_발행하고_발행_완료로_표시한다() {
    // given
    OutboxEventEntity appended = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0)));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    then(messageQueueSender).should().sendBatch(anyList());
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
    RelayedOutboxEvent relayed = onlySentEvent();
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
    assertThat(onlySentEvent().payload()).isEqualTo(objectMapper.writeValueAsString(event));
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
    // given — 셋 다 한 청크(10건 이하)에 들어가므로 같은 sendBatch 호출 하나 안에서 순서가 보존돼야 한다
    append(testEvent(3L, LocalDateTime.of(2026, 8, 5, 10, 0, 3)));
    append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 1)));
    append(testEvent(2L, LocalDateTime.of(2026, 8, 5, 10, 0, 2)));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    then(messageQueueSender).should(times(1)).sendBatch(anyList());
    assertThat(sentEventsOfOnlyCall())
        .extracting(RelayedOutboxEvent::aggregateId)
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  void 전송에_실패한_이벤트는_발행_완료로_표시하지_않는다() {
    // given
    OutboxEventEntity appended = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0)));
    given(messageQueueSender.sendBatch(anyList()))
        .willReturn(
            new BatchSendResult(List.of(), List.of(new FailedEvent(appended.getId(), "큐 장애"))));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(findById(appended.getId()).isPublished()).isFalse();
  }

  @Test
  void 호출_자체가_실패한_이벤트도_발행_완료로_표시하지_않는다() {
    // given — 네트워크 등으로 sendBatch 호출 자체가 예외를 던지는 경우
    OutboxEventEntity appended = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0)));
    willThrow(new IllegalStateException("큐 장애")).given(messageQueueSender).sendBatch(anyList());

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(findById(appended.getId()).isPublished()).isFalse();
  }

  @Test
  void 다른_애그리거트의_실패는_영향을_주지_않는다() {
    // given — 경매가 다르면 FIFO 그룹도 달라 순서를 함께 지킬 필요가 없다. 둘 다 한 청크에 들어간다
    OutboxEventEntity failing = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 1)));
    OutboxEventEntity succeeding = append(testEvent(2L, LocalDateTime.of(2026, 8, 5, 10, 0, 2)));
    given(messageQueueSender.sendBatch(anyList()))
        .willReturn(
            new BatchSendResult(
                List.of(succeeding.getId()), List.of(new FailedEvent(failing.getId(), "큐 장애"))));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    assertThat(findById(failing.getId()).isPublished()).isFalse();
    assertThat(findById(succeeding.getId()).isPublished()).isTrue();
  }

  @Test
  void 같은_청크_안에서는_그룹이_같아도_함께_전송된다() {
    // given — 알려진 한계: 같은 청크(같은 sendBatch 호출)에 실리면 앞쪽의 실패 여부와 무관하게 뒤쪽도 이미 같이 보내진 뒤다
    OutboxEventEntity first = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 1)));
    OutboxEventEntity second = append(testEvent(1L, LocalDateTime.of(2026, 8, 5, 10, 0, 2)));

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then — 실패 시뮬레이션 없이도 둘 다 같은 호출의 입력에 포함됐는지만 확인한다
    assertThat(sentEventsOfOnlyCall())
        .extracting(RelayedOutboxEvent::eventId)
        .containsExactlyInAnyOrder(first.getId(), second.getId());
  }

  @Test
  void 다음_청크에서는_실패한_애그리거트의_뒤_이벤트를_보내지_않는다() {
    // given — 첫 청크(10건)에 앞선 실패 이벤트를 채우고, 같은 그룹의 뒤 이벤트는 다음 청크로 넘긴다.
    // 다음 청크를 구성할 때는 이미 첫 청크의 실패로 그 그룹이 막혀 있어, sendBatch 호출 자체에 실리지 않는다.
    LocalDateTime base = LocalDateTime.of(2026, 8, 5, 10, 0, 0);
    OutboxEventEntity first = append(testEvent(1L, base.plusSeconds(1)));
    for (int i = 0; i < 9; i++) {
      append(testEvent(100L + i, base.plusSeconds(2 + i)));
    }
    OutboxEventEntity second = append(testEvent(1L, base.plusSeconds(20)));
    OutboxEventEntity otherAggregate = append(testEvent(2L, base.plusSeconds(21)));

    given(messageQueueSender.sendBatch(anyList()))
        .willAnswer(
            invocation -> {
              List<RelayedOutboxEvent> chunk = invocation.getArgument(0);
              boolean containsFirst =
                  chunk.stream().anyMatch(e -> e.eventId().equals(first.getId()));
              if (containsFirst) {
                return new BatchSendResult(
                    chunk.stream()
                        .filter(e -> !e.eventId().equals(first.getId()))
                        .map(RelayedOutboxEvent::eventId)
                        .toList(),
                    List.of(new FailedEvent(first.getId(), "큐 장애")));
              }
              return allSucceeded(chunk);
            });

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    then(messageQueueSender).should(times(2)).sendBatch(anyList());
    assertThat(findById(first.getId()).isPublished()).isFalse();
    assertThat(findById(second.getId()).isPublished()).isFalse();
    assertThat(findById(otherAggregate.getId()).isPublished()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void 십_건을_넘으면_여러_번의_배치_호출로_나눠_보낸다() {
    // given — 25건 → 10 + 10 + 5, 세 번의 sendBatch 호출로 나뉜다
    LocalDateTime base = LocalDateTime.of(2026, 8, 5, 10, 0, 0);
    for (int i = 0; i < 25; i++) {
      append(testEvent(1000L + i, base.plusSeconds(i)));
    }

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    ArgumentCaptor<List<RelayedOutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
    then(messageQueueSender).should(times(3)).sendBatch(captor.capture());
    assertThat(captor.getAllValues()).extracting(List::size).containsExactly(10, 10, 5);
  }

  @Test
  void 적체가_한_회차_처리량을_넘으면_한_번의_스케줄_호출_안에서_계속_이어_발행한다() {
    // given — 한 회차 조회 상한(100)을 넘는 101건. 전부 성공하므로 두 번째 회차가 곧바로 이어져야 한다
    LocalDateTime base = LocalDateTime.of(2026, 8, 5, 10, 0, 0);
    for (int i = 0; i < 101; i++) {
      append(testEvent(2000L + i, base.plusSeconds(i)));
    }

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then — 101건 / 10건씩 = 11번의 sendBatch 호출(10×10 + 1). 한 회차(최대 100건)만으로는 10번을 넘을 수 없으므로
    // 11번이 관측됐다는 것 자체가 두 번째 회차가 곧바로 이어졌다는 증거다.
    then(messageQueueSender).should(times(11)).sendBatch(anyList());
    assertThat(outboxEventJpaRepository.countByPublishedFalse()).isZero();
  }

  @Test
  void 발행_대상이_없으면_아무것도_하지_않는다() {
    // given — 적재분이 비어 있다

    // when
    outboxEventScheduler.relayUnpublishedEvents();

    // then
    then(messageQueueSender).shouldHaveNoInteractions();
  }

  private static BatchSendResult allSucceeded(List<RelayedOutboxEvent> events) {
    return new BatchSendResult(
        events.stream().map(RelayedOutboxEvent::eventId).toList(), List.of());
  }

  private RelayedOutboxEvent onlySentEvent() {
    List<RelayedOutboxEvent> sent = sentEventsOfOnlyCall();
    assertThat(sent).hasSize(1);
    return sent.get(0);
  }

  @SuppressWarnings("unchecked")
  private List<RelayedOutboxEvent> sentEventsOfOnlyCall() {
    ArgumentCaptor<List<RelayedOutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
    then(messageQueueSender).should().sendBatch(captor.capture());
    return captor.getValue();
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
