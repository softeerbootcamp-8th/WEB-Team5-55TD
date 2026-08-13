package com.ootd.pickup.global.event.messagequeue.outbox;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발행이 끝난 Outbox 행을 보존 기간이 지나면 지운다.
 *
 * <p>Outbox는 릴레이가 큐로 옮길 때까지만 필요한 임시 저장소다. {@link OutboxEventScheduler}가 {@code published=true}로 표시한
 * 뒤에는 그 행을 더 들여다볼 이유가 없는데도 지우는 로직이 없어 테이블이 무한정 쌓였다. 이 스케줄러가 그 뒤처리를 맡는다.
 *
 * <p><b>{@code published=false}인 행은 절대 지우지 않는다.</b> 발행에 계속 실패해 남아 있는 행(poison message)까지 보존 기간이
 * 지났다는 이유로 지우면 아직 큐에 들어가지 않은 이벤트가 그대로 유실된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(OutboxEventCleanupProperties.class)
@ConditionalOnProperty(name = "scheduler.outbox-cleanup.enabled", havingValue = "true")
public class OutboxEventCleanupScheduler {

  private final OutboxEventRepository outboxEventJpaRepository;
  private final OutboxEventCleanupProperties outboxEventCleanupProperties;

  @Scheduled(cron = "${scheduler.outbox-cleanup.cron:0 0 4 * * *}")
  @SchedulerLock(name = "outbox-event-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
  public void deletePublishedEventsBeforeRetention() {
    LocalDateTime threshold =
        LocalDateTime.now().minusDays(outboxEventCleanupProperties.retentionDays());
    int deleted = outboxEventJpaRepository.deleteByPublishedTrueAndCreatedAtBefore(threshold);
    log.info("Outbox 발행 완료 이벤트를 정리했습니다 - deleted={}, threshold={}", deleted, threshold);
  }
}
