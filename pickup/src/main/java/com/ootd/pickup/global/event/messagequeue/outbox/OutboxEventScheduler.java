package com.ootd.pickup.global.event.messagequeue.outbox;

import com.ootd.pickup.global.event.MessageQueueEvent;
import com.ootd.pickup.global.event.messagequeue.outbox.BatchSendResult.FailedEvent;
import com.ootd.pickup.global.observability.OutboxRelayMetrics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * <p><b>기본값이 꺼져 있다.</b> 보낼 큐가 아직 없어서다. {@code scheduler.outbox.enabled=true}로 켤 때는 {@code
 * event.sqs.enabled=true}도 함께 켜야 한다. 한쪽만 켜면 주입받을 {@link MessageQueueSender} 구현체가 없어 기동 단계에서 실패한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.outbox.enabled", havingValue = "true")
public class OutboxEventScheduler {

  /** 한 회차에 발행할 최대 건수. 잠금 점유 시간과 한 트랜잭션의 크기를 제한한다. */
  private static final Limit BATCH_LIMIT = Limit.of(100);

  /** SQS {@code SendMessageBatch} 호출 하나에 실을 수 있는 최대 건수. AWS 하드 리밋이다. */
  private static final int SEND_BATCH_SIZE = 10;

  /**
   * 한 번의 스케줄 호출 안에서 이어서 처리할 최대 회차 수.
   *
   * <p>적체가 없으면 회차 하나(대개 빈 조회)로 끝나 평소 폴링 비용은 그대로다. 적체가 있으면 외부 {@code fixedDelay}(기본 1초)를 기다리지 않고 곧바로
   * 다음 회차로 넘어가 계속 드레인한다 — 그 1초 텀이 적체 상황에서 처리량을 깎아 먹던 부분이다. 20회차 × 100건 = 최대 2000건이며, 회차당 배치 전송 소요
   * 시간을 감안해도 {@code lockAtMostFor}(30초)에 비해 충분히 여유 있다.
   */
  private static final int MAX_DRAIN_ITERATIONS_PER_INVOCATION = 20;

  private final OutboxEventRepository outboxEventJpaRepository;
  private final MessageQueueSender messageQueueSender;
  private final TransactionTemplate transactionTemplate;
  private final OutboxRelayMetrics metrics;

  /**
   * 발행 대기 중인 이벤트를 큐로 보내고 발행 완료로 표시하기를, 처리할 게 없어질 때까지(또는 회차 상한까지) 반복한다.
   *
   * <p>{@code blockedGroups}를 회차 사이에 공유한다 — 회차마다 새로 만들면, 어떤 회차에서 실패해 막힌 애그리거트가 다음 회차에서는 다시 막힘 없이
   * 재시도돼 같은 그룹 안에서 순서가 역전될 수 있다. 이 드레인 루프는 외부 {@code fixedDelay} 없이 곧바로 이어지므로, 실패한 그룹은 이번 스케줄 호출이
   * 끝날 때까지 계속 막혀 있어야 회차 하나짜리였던 기존의 순서 보장이 그대로 유지된다.
   *
   * <p>회차 하나의 세부 동작은 {@link #relayOneBatch(Set)} 참고.
   */
  @Scheduled(fixedDelayString = "${scheduler.outbox.fixed-delay:1s}")
  @SchedulerLock(name = "outbox-event-relay", lockAtMostFor = "PT30S", lockAtLeastFor = "PT0.1S")
  public void relayUnpublishedEvents() {
    Set<String> blockedGroups = new HashSet<>();
    for (int iteration = 0; iteration < MAX_DRAIN_ITERATIONS_PER_INVOCATION; iteration++) {
      if (!relayOneBatch(blockedGroups)) {
        return;
      }
    }
  }

  /**
   * 발행 대기 중인 이벤트 한 회차(최대 {@code BATCH_LIMIT}건)를 큐로 보내고 발행 완료로 표시한다.
   *
   * <p>전송이 먼저, 표시가 나중이다. 순서를 뒤집으면 표시한 뒤 전송이 실패했을 때 그 이벤트가 영구히 사라진다. 이 순서에서는 전송 후 커밋이 실패해도 유실 대신 중복이
   * 생기고, 중복은 소비자가 {@link MessageQueueEvent#eventId()}로 걸러낼 수 있다.
   *
   * <p>트랜잭션을 걸지 않고 <b>짧은 읽기 → 트랜잭션 밖 전송 → 짧은 갱신</b>으로 나눈다. 큐 전송은 외부 통신이라 응답이 늦을 수 있고 한 회차에 최대
   * {@code BATCH_LIMIT}건을 보내므로, 트랜잭션 안에서 보내면 그 시간 내내 DB 커넥션을 붙잡아 요청 처리 쪽 커넥션이 고갈된다.
   *
   * <p>{@code SEND_BATCH_SIZE}건씩 묶어 {@link MessageQueueSender#sendBatch}로 보낸다. 청크를 만들 때 이미 실패해 차단된
   * 애그리거트의 이벤트는 건너뛴다 — 계속 보내면 뒤 이벤트가 큐에 먼저 들어가고 다음 회차가 앞 이벤트를 재시도해 같은 그룹 안에서 순서가 역전된다. 건너뛴 이벤트는
   * {@code published=false}로 남아 다음 회차에 순서대로 다시 시도된다. 다른 애그리거트는 그룹이 달라 계속 발행한다.
   *
   * <p><b>알려진 한계.</b> 같은 그룹의 이벤트 둘이 같은 청크(같은 {@code SendMessageBatch} 호출) 안에 있으면, 앞쪽이 실패해도 이미 같은
   * 호출에 함께 실려 보내진 뒤쪽은 막을 수 없다 — SQS가 그 순간 실제로 무엇을 큐에 넣었는지는 응답으로만 안다. 청크 경계를 넘어선 뒤 이벤트는 기존과 동일하게 확실히
   * 막힌다. 배치 항목 실패는 대부분 요청 자체의 문제(형식 오류 등)지 일시적 재시도 대상이 아니라서, 건별 전송 대비 감수할 만한 아주 드문 잔여 리스크로 판단했다.
   *
   * @param blockedGroups 이번 스케줄 호출 안에서 이미 실패해 막힌 애그리거트. 이번 회차에서 새로 실패한 애그리거트를 여기 추가해 다음 회차부터 반영되게
   *     한다
   * @return 이번 회차에 하나라도 실제로 발행에 성공했으면 {@code true}. 호출자가 이 값으로 다음 회차를 곧바로 이어서 부를지 판단한다 — 대기 중인 게
   *     있어도 전부 실패했으면(예: SQS 장애) {@code false}를 돌려줘 외부 {@code fixedDelay} 만큼 쉬게 한다. 그렇지 않으면 같은 장애가
   *     지수 백오프 없이 회차 상한만큼 연속으로 재시도된다
   */
  private boolean relayOneBatch(Set<String> blockedGroups) {
    List<RelayedOutboxEvent> pending = findPendingEvents();
    // pending.size()는 한 회차 처리량(BATCH_LIMIT)에서 잘려 실제 적체 규모를 반영하지 못하므로 전체 건수를 따로 센다.
    // 큐가 비어도(이 조회가 0을 돌려줘도) 게이지가 이전 값에 멈춰 있지 않도록 이른 반환 전에 갱신한다.
    metrics.recordPendingCount(outboxEventJpaRepository.countByPublishedFalse());
    if (pending.isEmpty()) {
      return false;
    }

    Map<String, RelayedOutboxEvent> eventsById =
        pending.stream()
            .collect(Collectors.toMap(RelayedOutboxEvent::eventId, Function.identity()));
    List<String> publishedIds = new ArrayList<>();
    List<String> failedIds = new ArrayList<>();
    List<String> failureDetails = new ArrayList<>();
    int skipped = 0;

    for (int start = 0; start < pending.size(); start += SEND_BATCH_SIZE) {
      List<RelayedOutboxEvent> chunk =
          pending.subList(start, Math.min(start + SEND_BATCH_SIZE, pending.size()));

      List<RelayedOutboxEvent> toSend = new ArrayList<>(chunk.size());
      for (RelayedOutboxEvent event : chunk) {
        if (blockedGroups.contains(event.messageGroupId())) {
          skipped++;
          continue;
        }
        toSend.add(event);
      }
      if (toSend.isEmpty()) {
        continue;
      }

      BatchSendResult result;
      try {
        result = messageQueueSender.sendBatch(toSend);
      } catch (RuntimeException exception) {
        // 호출 자체가 실패했다 - 이 청크 전체를 실패로 취급하고 각 애그리거트를 막는다.
        for (RelayedOutboxEvent event : toSend) {
          failedIds.add(event.eventId());
          failureDetails.add(event.eventId() + "(호출 자체 실패: " + exception + ")");
          blockedGroups.add(event.messageGroupId());
        }
        log.error(
            "Outbox 이벤트 배치 전송 호출이 실패했습니다 - count={}, eventIds={}",
            toSend.size(),
            toSend.stream().map(RelayedOutboxEvent::eventId).toList(),
            exception);
        continue;
      }

      for (String succeededId : result.succeededEventIds()) {
        publishedIds.add(succeededId);
        RelayedOutboxEvent event = eventsById.get(succeededId);
        metrics.recordEventAge(
            Duration.between(event.occurredAt(), LocalDateTime.now(ZoneOffset.UTC)));
      }
      for (FailedEvent failed : result.failedEvents()) {
        failedIds.add(failed.eventId());
        failureDetails.add(failed.eventId() + "(" + failed.reason() + ")");
        blockedGroups.add(eventsById.get(failed.eventId()).messageGroupId());
      }
    }

    // 반복문 안에서 건별로 남기면 같은 실패가 매 회차 쏟아진다. 한 줄로 모아 남긴다.
    if (!failedIds.isEmpty()) {
      log.error(
          "Outbox 이벤트 발행에 실패했습니다 - count={}, skipped={}, failures={}",
          failedIds.size(),
          skipped,
          failureDetails);
    }

    if (publishedIds.isEmpty()) {
      return false;
    }

    int marked = markPublished(publishedIds);
    log.info(
        "Outbox 이벤트를 발행했습니다 - published={}, failed={}, skipped={}",
        marked,
        failedIds.size(),
        skipped);
    return true;
  }

  /** 발행 대기 중인 행을 읽어 전송 대상으로 바꾼다. 이 트랜잭션은 조회에만 쓰이고 바로 닫힌다. */
  private List<RelayedOutboxEvent> findPendingEvents() {
    List<RelayedOutboxEvent> pending =
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
