package com.ootd.pickup.global.event.outbox;

import com.ootd.pickup.global.event.MessageQueueEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox에 쌓인 메시지 큐 이벤트를 실제 큐로 옮기는 릴레이.
 *
 * <p>Outbox 패턴의 뒤쪽 절반이다. 도메인 트랜잭션은 적재까지만 책임지고 외부 전송을 별도 주기로 분리한다. 그래야 큐가 잠시 죽어도 도메인 트랜잭션이 실패하지 않고,
 * 이미 커밋된 이벤트는 큐가 살아난 뒤 그대로 전달된다.
 *
 * <p><b>기본값이 꺼져 있다.</b> {@link MessageQueueSender} 구현체가 아직 빈 구현이라 켜면 전송이 성공한 것처럼 보여 행을 발행 완료로 표시하고,
 * 유실이 허용되지 않는 이벤트가 조용히 사라진다. 실제 전송이 구현된 뒤 {@code scheduler.outbox.enabled=true}로 켠다.
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
  private final TransactionTemplate transactionTemplate;

  /**
   * 발행 대기 중인 이벤트를 큐로 보내고 발행 완료로 표시한다.
   *
   * <p>전송이 먼저, 표시가 나중이다. 순서를 뒤집으면 표시한 뒤 전송이 실패했을 때 그 이벤트가 영구히 사라진다. 이 순서에서는 전송 후 커밋이 실패해도 유실 대신 중복이
   * 생기고, 중복은 소비자가 {@link MessageQueueEvent#eventId()}로 걸러낼 수 있다.
   *
   * <p>트랜잭션을 걸지 않고 <b>짧은 읽기 → 트랜잭션 밖 전송 → 짧은 갱신</b>으로 나눈다. 큐 전송은 외부 통신이라 응답이 늦을 수 있고 한 주기에 최대
   * {@code BATCH_LIMIT}건을 보내므로, 트랜잭션 안에서 보내면 그 시간 내내 DB 커넥션을 붙잡아 요청 처리 쪽 커넥션이 고갈된다.
   *
   * <p>건별로 예외를 격리하되, 실패한 애그리거트의 뒤 이벤트는 이번 주기에 보내지 않는다. 계속 보내면 뒤 이벤트가 큐에 먼저 들어가고 다음 주기가 앞 이벤트를 재시도해
   * 같은 그룹 안에서 순서가 역전된다. 건너뛴 이벤트는 {@code published=false}로 남아 다음 주기에 순서대로 다시 시도된다. 다른 애그리거트는 그룹이 달라
   * 계속 발행한다.
   */
  @Scheduled(fixedDelayString = "${scheduler.outbox.fixed-delay:1s}")
  @SchedulerLock(name = "outbox-event-relay", lockAtMostFor = "PT30S", lockAtLeastFor = "PT0.5S")
  public void relayUnpublishedEvents() {
    List<MessageQueueEvent> pending = findPendingEvents();
    if (pending.isEmpty()) {
      return;
    }

    List<String> publishedIds = new ArrayList<>();
    List<String> failedIds = new ArrayList<>();
    Set<String> blockedGroups = new HashSet<>();
    int skipped = 0;
    RuntimeException firstFailure = null;

    for (MessageQueueEvent event : pending) {
      String group = messageGroupOf(event);
      if (blockedGroups.contains(group)) {
        skipped++;
        continue;
      }

      try {
        messageQueueSender.send(event);
        publishedIds.add(event.eventId());
      } catch (RuntimeException exception) {
        blockedGroups.add(group);
        failedIds.add(event.eventId());
        if (firstFailure == null) {
          firstFailure = exception;
        }
      }
    }

    // 반복문 안에서 건별로 남기면 같은 실패가 매 주기 쏟아진다. 한 줄로 모아 남긴다.
    if (!failedIds.isEmpty()) {
      log.error(
          "Outbox 이벤트 발행에 실패했습니다 - count={}, skipped={}, eventIds={}",
          failedIds.size(),
          skipped,
          failedIds,
          firstFailure);
    }

    if (publishedIds.isEmpty()) {
      return;
    }

    int marked = markPublished(publishedIds);
    log.info(
        "Outbox 이벤트를 발행했습니다 - published={}, failed={}, skipped={}",
        marked,
        failedIds.size(),
        skipped);
  }

  /**
   * 순서를 함께 지켜야 하는 단위.
   *
   * <p>{@link MessageQueueSender} 구현체가 {@code aggregateType}과 {@code aggregateId}로 FIFO 큐의 {@code
   * MessageGroupId}를 만들므로 여기서도 같은 두 값으로 묶는다. 문자열 형식은 달라도 되지만 묶는 기준이 달라지면 차단이 헛돈다.
   */
  private String messageGroupOf(MessageQueueEvent event) {
    return event.aggregateType() + ":" + event.aggregateId();
  }

  /** 발행 대기 중인 행을 읽어 전송 대상으로 바꾼다. 이 트랜잭션은 조회에만 쓰이고 바로 닫힌다. */
  private List<MessageQueueEvent> findPendingEvents() {
    List<MessageQueueEvent> pending =
        transactionTemplate.execute(
            status ->
                outboxEventJpaRepository
                    .findAllByPublishedFalseOrderByCreatedAtAsc(BATCH_LIMIT)
                    .stream()
                    .map(OutboxEventEntity::toEvent)
                    .toList());
    return pending == null ? List.of() : pending;
  }

  /** 전송에 성공한 행만 발행 완료로 표시한다. 갱신 한 문장만 담는 짧은 트랜잭션이다. */
  private int markPublished(List<String> publishedIds) {
    Integer marked =
        transactionTemplate.execute(
            status -> outboxEventJpaRepository.updatePublishedByIdIn(publishedIds));
    return marked == null ? 0 : marked;
  }
}
