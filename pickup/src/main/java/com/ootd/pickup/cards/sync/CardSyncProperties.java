package com.ootd.pickup.cards.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scheduler.card-sync")
public record CardSyncProperties(int recentDays, int maxSetsPerRun) {
  public CardSyncProperties {
    if (recentDays <= 0) {
      recentDays = 90;
    }
    if (maxSetsPerRun <= 0) {
      maxSetsPerRun = 10;
    }
  }
}
