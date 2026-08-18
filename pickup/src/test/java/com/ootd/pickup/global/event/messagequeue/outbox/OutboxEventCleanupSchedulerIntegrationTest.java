package com.ootd.pickup.global.event.messagequeue.outbox;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 정리 스케줄러는 기본적으로 꺼져 있으므로 이 테스트에서만 켠다. */
@SpringBootTest(
    properties = {
      "scheduler.outbox-cleanup.enabled=true",
      "scheduler.outbox-cleanup.retention-days=14"
    })
@ActiveProfiles("test")
@Transactional
class OutboxEventCleanupSchedulerIntegrationTest {

  @Autowired private OutboxEventCleanupScheduler outboxEventCleanupScheduler;

  @Autowired private OutboxEventRepository outboxEventJpaRepository;

  @Autowired private EventProducer eventProducer;

  @Autowired private EntityManager entityManager;

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

  @BeforeEach
  void 이전_테스트가_남긴_적재분을_비운다() {
    outboxEventJpaRepository.deleteAll();
    entityManager.flush();
  }

  @Test
  void 보존_기간이_지난_발행_완료_이벤트를_지운다() {
    // given
    LocalDateTime old = LocalDateTime.now().minusDays(15);
    OutboxEventEntity appended = appendPublished(testEvent(1L, old));

    // when
    outboxEventCleanupScheduler.deletePublishedEventsBeforeRetention();

    // then
    entityManager.flush();
    entityManager.clear();
    assertThat(outboxEventJpaRepository.findById(appended.getId())).isEmpty();
  }

  @Test
  void 보존_기간이_지나지_않은_발행_완료_이벤트는_지우지_않는다() {
    // given
    LocalDateTime recent = LocalDateTime.now().minusDays(1);
    OutboxEventEntity appended = appendPublished(testEvent(1L, recent));

    // when
    outboxEventCleanupScheduler.deletePublishedEventsBeforeRetention();

    // then
    entityManager.flush();
    entityManager.clear();
    assertThat(outboxEventJpaRepository.findById(appended.getId())).isPresent();
  }

  @Test
  void 발행되지_않은_이벤트는_보존_기간이_지나도_지우지_않는다() {
    // given — poison message. published=false인 채 오래 남아 있어도 유실되면 안 된다
    LocalDateTime old = LocalDateTime.now().minusDays(30);
    OutboxEventEntity appended = append(testEvent(1L, old));

    // when
    outboxEventCleanupScheduler.deletePublishedEventsBeforeRetention();

    // then
    entityManager.flush();
    entityManager.clear();
    assertThat(outboxEventJpaRepository.findById(appended.getId())).isPresent();
  }

  private TestEvent testEvent(Long auctionId, LocalDateTime occurredAt) {
    return new TestEvent(UUID.randomUUID().toString(), auctionId, occurredAt);
  }

  private OutboxEventEntity append(MessageQueueEvent event) {
    eventProducer.produce(event);
    entityManager.flush();
    return outboxEventJpaRepository.findById(event.eventId()).orElseThrow();
  }

  private OutboxEventEntity appendPublished(MessageQueueEvent event) {
    OutboxEventEntity appended = append(event);
    outboxEventJpaRepository.updatePublishedByIdIn(List.of(appended.getId()));
    entityManager.flush();
    entityManager.clear();
    return outboxEventJpaRepository.findById(appended.getId()).orElseThrow();
  }
}
