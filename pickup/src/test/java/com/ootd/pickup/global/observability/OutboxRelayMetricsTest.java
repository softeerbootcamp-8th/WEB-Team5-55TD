package com.ootd.pickup.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRelayMetricsTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final OutboxRelayMetrics metrics = new OutboxRelayMetrics(meterRegistry);

  @Test
  void 이벤트_나이를_기록하면_타이머에_반영된다() {
    metrics.recordEventAge(Duration.ofMillis(1500));
    metrics.recordEventAge(Duration.ofMillis(500));

    assertThat(meterRegistry.get("pickup.outbox.event.age").timer().count()).isEqualTo(2);
    assertThat(
            meterRegistry
                .get("pickup.outbox.event.age")
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
        .isEqualTo(2000);
  }

  @Test
  void 대기_건수를_기록하면_게이지가_그_값으로_갱신된다() {
    metrics.recordPendingCount(42);

    assertThat(meterRegistry.get("pickup.outbox.pending.count").gauge().value()).isEqualTo(42);

    metrics.recordPendingCount(0);

    assertThat(meterRegistry.get("pickup.outbox.pending.count").gauge().value()).isEqualTo(0);
  }
}
