package com.ootd.pickup.global.event.messagequeue.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scheduler.outbox-cleanup")
public record OutboxEventCleanupProperties(int retentionDays) {
  public OutboxEventCleanupProperties {
    if (retentionDays <= 0) {
      retentionDays = 14;
    }
  }
}
