package com.ootd.pickup.global.event.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
import com.ootd.pickup.auction.event.WinningBidSnapshot;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.EventPublisher;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림이 <b>실제 Redis Pub/Sub을 거쳐</b> 핸들러까지 도달하는지 확인한다.
 *
 * <p>발행(직렬화·채널 계산) → Redis → 구독(역직렬화·채널 검증·디스패치)을 한 번에 이어 붙인다. 다른 Redis 테스트는 {@code
 * StringRedisTemplate}과 {@code Message}를 목으로 대체해 양쪽을 따로 본다.
 *
 * <p>{@code localhost:6379}에 Redis가 떠 있어야 한다. 없으면 {@code redisMessageListenerContainer} 기동이 실패해
 * 컨텍스트가 로드되지 않는다.
 *
 * <p>수신은 구독 스레드에서 일어나므로 큐로 받아 기다린다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(RedisNotificationRoundTripIntegrationTest.RoundTripHandlerConfig.class)
class RedisNotificationRoundTripIntegrationTest {

  private static final long AUCTION_ID = 987_654_321L;

  @Autowired private EventPublisher eventPublisher;

  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired private RoundTripHandler roundTripHandler;

  @Test
  void 커밋되면_알림이_Redis를_거쳐_핸들러까지_도달한다() throws InterruptedException {
    // given
    AuctionBidUpdatedNotificationEvent event = testEvent();

    // when
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

    // then
    AuctionBidUpdatedNotificationEvent received = roundTripHandler.awaitFor(AUCTION_ID);
    assertThat(received).isNotNull();
    assertThat(received).isEqualTo(event);
  }

  @Test
  void 왕복한_알림은_중첩된_낙찰_입찰_스냅샷까지_보존한다() throws InterruptedException {
    // given — 봉투는 payload를 JsonNode로 감싸므로 중첩 record와 enum, 시각이 함께 복원되어야 한다
    AuctionBidUpdatedNotificationEvent event = testEvent();

    // when
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

    // then
    AuctionBidUpdatedNotificationEvent received = roundTripHandler.awaitFor(AUCTION_ID);
    assertThat(received).isNotNull();
    assertThat(received.eventType()).isEqualTo(event.eventType());
    assertThat(received.auctionStatus()).isEqualTo(AuctionStatus.ONGOING);
    assertThat(received.occurredAt()).isEqualTo(event.occurredAt());
    assertThat(received.winningBid()).isEqualTo(event.winningBid());
  }

  private AuctionBidUpdatedNotificationEvent testEvent() {
    LocalDateTime now = LocalDateTime.now().withNano(0);
    return new AuctionBidUpdatedNotificationEvent(
        UUID.randomUUID().toString(),
        AUCTION_ID,
        100L,
        10_000L,
        15_000L,
        10_500L,
        AuctionStatus.ONGOING,
        now.minusHours(1),
        now.plusHours(1),
        now.minusHours(2),
        new WinningBidSnapshot(10L, 2L, "왕복테스트", 10_500L, BidStatus.HIGHEST, now),
        now);
  }

  @TestConfiguration
  static class RoundTripHandlerConfig {

    @Bean
    RoundTripHandler roundTripHandler() {
      return new RoundTripHandler();
    }
  }

  static class RoundTripHandler implements EventHandler<AuctionBidUpdatedNotificationEvent> {

    private final BlockingQueue<AuctionBidUpdatedNotificationEvent> received =
        new ArrayBlockingQueue<>(16);

    @Override
    public Class<AuctionBidUpdatedNotificationEvent> eventClass() {
      return AuctionBidUpdatedNotificationEvent.class;
    }

    @Override
    public void handle(AuctionBidUpdatedNotificationEvent event) {
      received.offer(event);
    }

    /** JVM을 공유하는 다른 테스트의 알림이 섞일 수 있어 이 테스트가 만든 경매의 것만 골라 기다린다. */
    AuctionBidUpdatedNotificationEvent awaitFor(Long auctionId) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline) {
        AuctionBidUpdatedNotificationEvent event =
            received.poll(TimeUnit.SECONDS.toNanos(5), TimeUnit.NANOSECONDS);
        if (event == null) {
          return null;
        }
        if (auctionId.equals(event.auctionId())) {
          return event;
        }
      }
      return null;
    }
  }
}
