package com.ootd.pickup.global.event.messagequeue.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발행이 끝난 Outbox 행을 보존 기간이 지나면 지우는 주기 작업.
 *
 * <p>정리 로직 자체는 {@link OutboxEventCleanupService}에 있다. 이 클래스는 스케줄 트리거와 인스턴스 간 중복 실행
 * 방지({@code @SchedulerLock})만 맡는다. {@link com.ootd.pickup.cards.sync.CardSyncScheduler}와 같은 역할 분리다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.outbox-cleanup.enabled", havingValue = "true")
public class OutboxEventCleanupScheduler {

  private final OutboxEventCleanupService outboxEventCleanupService;

  @Scheduled(cron = "${scheduler.outbox-cleanup.cron:0 0 4 * * *}")
  @SchedulerLock(name = "outbox-event-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
  public void deletePublishedEventsBeforeRetention() {
    int deleted = outboxEventCleanupService.deleteExpiredEvents();
    log.info("Outbox 발행 완료 이벤트를 정리했습니다 - deleted={}", deleted);
  }
}
