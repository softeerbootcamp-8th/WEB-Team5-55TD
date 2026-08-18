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
 *
 * <p>{@code zone = "Asia/Seoul"}을 명시한다. {@link com.ootd.pickup.PickupApplication}이 JVM 기본 타임존을 UTC로
 * 고정해두므로, zone을 지정하지 않으면 cron 값이 UTC 기준으로 해석되어 "새벽 4시"라는 cron 표현식의 의도와 실제 실행 시각(한국시간 낮 1시)이 어긋난다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.outbox-cleanup.enabled", havingValue = "true")
public class OutboxEventCleanupScheduler {

  private final OutboxEventCleanupService outboxEventCleanupService;

  @Scheduled(cron = "${scheduler.outbox-cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
  @SchedulerLock(name = "outbox-event-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
  public void deletePublishedEventsBeforeRetention() {
    int deleted = outboxEventCleanupService.deleteExpiredEvents();
    log.info("Outbox 발행 완료 이벤트를 정리했습니다 - deleted={}", deleted);
  }
}
