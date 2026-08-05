package com.ootd.pickup.global.event.outbox;

import com.ootd.pickup.global.event.MessageQueueEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox에 쌓인 메시지 큐 이벤트를 실제 큐로 옮기는 릴레이.
 *
 * <p>Outbox 패턴의 뒤쪽 절반이다. 도메인 트랜잭션은 이벤트를 테이블에 적재하는 데까지만 책임지고 외부 전송은 별도 주기로 분리한다. 그래야 큐가 잠시 죽어도 도메인
 * 트랜잭션이 실패하지 않고, 이미 커밋된 이벤트는 큐가 살아난 뒤 그대로 전달된다.
 *
 * <p>기본값이 꺼져 있다. {@link MessageQueueSender} 구현체가 아직 빈 구현이라, 켜면 전송이 성공한 것처럼 보여 행을 발행 완료로 표시하고 유실이
 * 허용되지 않는 이벤트가 조용히 사라진다. 실제 전송이 구현된 뒤 {@code scheduler.outbox.enabled=true}로 켠다. 그때까지 이벤트는 사라지지 않고
 * 테이블에 쌓인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.outbox.enabled", havingValue = "true")
public class OutboxEventScheduler {

  /** 한 주기에 발행할 최대 건수. 잠금 점유 시간과 한 트랜잭션의 크기를 제한한다. */
  private static final Limit BATCH_LIMIT = Limit.of(100);

  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final MessageQueueSender messageQueueSender;

  /**
   * 발행 대기 중인 이벤트를 큐로 보내고 발행 완료로 표시한다.
   *
   * <p>전송이 먼저, 표시가 나중이다. 순서를 뒤집으면 표시한 뒤 전송이 실패했을 때 그 이벤트가 영구히 사라진다. 이 순서에서는 전송 후 커밋이 실패하면 다음 주기가 같은
   * 이벤트를 다시 보내므로 유실 대신 중복이 생긴다. 중복은 소비자가 {@link MessageQueueEvent#eventId()}로 걸러낼 수 있지만 유실은 되돌릴 수
   * 없다.
   *
   * <p>건별로 예외를 격리한다. 한 건이 실패해도 나머지는 발행되고, 실패한 행만 {@code published=false}로 남아 다음 주기에 다시 시도된다.
   */
  @Scheduled(fixedDelayString = "${scheduler.outbox.fixed-delay:1s}")
  @SchedulerLock(name = "outbox-event-relay", lockAtMostFor = "PT30S", lockAtLeastFor = "PT0.5S")
  @Transactional
  public void relayUnpublishedEvents() {
    List<OutboxEventEntity> unpublished =
        outboxEventJpaRepository.findAllByPublishedFalseOrderByCreatedAtAsc(BATCH_LIMIT);
    if (unpublished.isEmpty()) {
      return;
    }

    List<String> publishedIds = new ArrayList<>();
    List<String> failedIds = new ArrayList<>();
    RuntimeException firstFailure = null;

    for (OutboxEventEntity outboxEvent : unpublished) {
      try {
        messageQueueSender.send(outboxEvent.toEvent());
        publishedIds.add(outboxEvent.getId());
      } catch (RuntimeException exception) {
        failedIds.add(outboxEvent.getId());
        if (firstFailure == null) {
          firstFailure = exception;
        }
      }
    }

    // 반복문 안에서 건별로 남기면 같은 실패가 매 주기 쏟아진다. 한 줄로 모아 남긴다.
    if (!failedIds.isEmpty()) {
      log.error(
          "Outbox 이벤트 발행에 실패했습니다 - count={}, eventIds={}",
          failedIds.size(),
          failedIds,
          firstFailure);
    }

    if (publishedIds.isEmpty()) {
      return;
    }

    int marked = outboxEventJpaRepository.updatePublishedByIdIn(publishedIds);
    log.info("Outbox 이벤트를 발행했습니다 - published={}, failed={}", marked, failedIds.size());
  }
}
