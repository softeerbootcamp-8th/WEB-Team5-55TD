package com.ootd.pickup.global.event.messagequeue.outbox;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class OutboxEventJpaRepositoryIntegrationTest {

  @Autowired private OutboxEventJpaRepository outboxEventJpaRepository;

  @Autowired private EventProducer eventProducer;

  @Autowired private EntityManager entityManager;

  @Autowired private ObjectMapper objectMapper;

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

  @Test
  @Transactional
  void 적재한_행은_payload_원문까지_그대로_왕복한다() {
    // given
    TestEvent event =
        new TestEvent(
            UUID.randomUUID().toString(), 1024L, 50000L, LocalDateTime.of(2026, 8, 5, 10, 30, 0));
    String payload = objectMapper.writeValueAsString(event);

    // when
    eventProducer.produce(event);
    entityManager.flush();
    entityManager.clear();

    // then
    OutboxEventEntity saved = outboxEventJpaRepository.findById(event.eventId()).orElseThrow();
    assertThat(saved.getPayload()).isEqualTo(payload);
    assertThat(saved.getAggregateType()).isEqualTo(AggregateType.AUCTION);
    assertThat(saved.getAggregateId()).isEqualTo(1024L);
    assertThat(saved.getEventType()).isEqualTo(EventType.AUCTION_ENDED);
    assertThat(saved.isPublished()).isFalse();
    assertThat(saved.getCreatedAt()).isEqualTo(event.occurredAt());
  }

  @Test
  @Transactional
  void 여러_이벤트를_적재하면_조회없이_한_문장으로_INSERT된다() {
    // given
    List<TestEvent> events =
        IntStream.rangeClosed(1, 3)
            .mapToObj(
                index ->
                    new TestEvent(
                        UUID.randomUUID().toString(),
                        (long) index,
                        index * 1000L,
                        LocalDateTime.of(2026, 8, 5, 10, 30, index)))
            .toList();
    Statistics statistics = statistics();
    statistics.clear();

    // when
    events.forEach(eventProducer::produce);
    entityManager.flush();

    // then
    assertThat(statistics.getEntityInsertCount()).isEqualTo(3);
    assertThat(statistics.getEntityLoadCount()).isZero();
    // produce() 는 한 건씩 받지만 persist 가 영속성 컨텍스트에 쌓이기만 하므로 flush 때
    // 한 문장으로 합쳐진다. OutboxEventEntity 가 Persistable 을 구현하지 않으면 save 가
    // merge 로 가고 행마다 존재 확인 SELECT 가 붙어 이 값이 4가 된다(측정값). 그 SELECT 는
    // 행을 찾지 못하므로 위의 getEntityLoadCount 로는 잡히지 않는다.
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  private Statistics statistics() {
    return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
  }
}
