package com.ootd.pickup.global.event.messagequeue.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 배치 크기와 주기당 삭제 상한이 실제로 지켜지는지 확인한다. */
@SpringBootTest(
    properties = {
      "scheduler.outbox-cleanup.retention-days=14",
      "scheduler.outbox-cleanup.batch-size=2",
      "scheduler.outbox-cleanup.max-deletes-per-run=4"
    })
@ActiveProfiles("test")
@Transactional
class OutboxEventCleanupServiceIntegrationTest {

  @Autowired private OutboxEventCleanupService outboxEventCleanupService;

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
  void 배치_크기보다_많은_발행_완료_이벤트도_전부_지운다() {
    // given — batch-size=2보다 많고 max-deletes-per-run=4보다는 적은 3건을 오래된 값으로 적재
    List<String> ids = appendPublished(3, LocalDateTime.now().minusDays(30));

    // when
    int deleted = outboxEventCleanupService.deleteExpiredEvents();

    // then
    assertThat(deleted).isEqualTo(3);
    entityManager.flush();
    entityManager.clear();
    ids.forEach(id -> assertThat(outboxEventJpaRepository.findById(id)).isEmpty());
  }

  @Test
  void 주기당_삭제_상한에_도달하면_남은_행은_다음_주기로_넘긴다() {
    // given — max-deletes-per-run=4보다 많은 6건
    List<String> ids = appendPublished(6, LocalDateTime.now().minusDays(30));

    // when
    int deleted = outboxEventCleanupService.deleteExpiredEvents();

    // then
    assertThat(deleted).isEqualTo(4);
    entityManager.flush();
    entityManager.clear();
    long remaining =
        ids.stream().filter(id -> outboxEventJpaRepository.findById(id).isPresent()).count();
    assertThat(remaining).isEqualTo(2);
  }

  private List<String> appendPublished(int count, LocalDateTime baseOccurredAt) {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      TestEvent event =
          new TestEvent(UUID.randomUUID().toString(), (long) i, baseOccurredAt.plusSeconds(i));
      eventProducer.produce(event);
      ids.add(event.eventId());
    }
    entityManager.flush();
    outboxEventJpaRepository.updatePublishedByIdIn(ids);
    entityManager.flush();
    entityManager.clear();
    return ids;
  }
}
