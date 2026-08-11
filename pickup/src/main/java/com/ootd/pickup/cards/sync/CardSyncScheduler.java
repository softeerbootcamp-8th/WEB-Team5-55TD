package com.ootd.pickup.cards.sync;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CardSyncProperties.class)
@ConditionalOnProperty(
    name = "scheduler.card-sync.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CardSyncScheduler {

  private final CardSyncService cardSyncService;

  @Scheduled(cron = "${scheduler.card-sync.cron:0 0 */6 * * *}")
  @SchedulerLock(name = "card-sync", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
  public void synchronizeCards() {
    cardSyncService.synchronizeCards();
  }
}
