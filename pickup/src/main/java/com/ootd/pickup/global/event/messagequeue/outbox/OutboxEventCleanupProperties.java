package com.ootd.pickup.global.event.messagequeue.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scheduler.outbox-cleanup")
public record OutboxEventCleanupProperties(int retentionDays, int batchSize, int maxDeletesPerRun) {
  public OutboxEventCleanupProperties {
    if (retentionDays <= 0) {
      retentionDays = 14;
    }
    if (batchSize <= 0) {
      batchSize = 500;
    }
    if (maxDeletesPerRun <= 0) {
      maxDeletesPerRun = 50_000;
    }
  }
}
