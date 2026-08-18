package com.ootd.pickup.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.global.observability.SQSEventConsumerMetrics.BatchOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class SQSEventConsumerMetricsTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final SQSEventConsumerMetrics metrics = new SQSEventConsumerMetrics(meterRegistry);

  @Test
  void 사건_종류와_성공_여부로_메시지_처리시간을_기록한다() {
    // when
    metrics.recordMessageDuration("BID_REQUEST_CREATED", true, Duration.ofMillis(120));

    // then
    assertThat(
            meterRegistry
                .get("pickup.sqs.consumer.message.duration")
                .tags("event_type", "BID_REQUEST_CREATED", "outcome", "success")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isCloseTo(120.0, Offset.offset(5.0));
  }

  @Test
  void 배치_결과로_처리시간과_크기를_기록한다() {
    // when
    metrics.recordBatch(BatchOutcome.PARTIAL_FAILURE, 10, Duration.ofMillis(350));

    // then
    assertThat(
            meterRegistry
                .get("pickup.sqs.consumer.batch.duration")
                .tag("outcome", "partial_failure")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isCloseTo(350.0, Offset.offset(5.0));
    assertThat(
            meterRegistry
                .get("pickup.sqs.consumer.batch.size")
                .tag("outcome", "partial_failure")
                .summary()
                .totalAmount())
        .isEqualTo(10.0);
  }

  @Test
  void 재전달된_메시지를_사건_종류별로_기록한다() {
    // when
    metrics.recordRedelivery("AUCTION_ENDED");
    metrics.recordRedelivery("AUCTION_ENDED");

    // then
    assertThat(
            meterRegistry
                .get("pickup.sqs.message.redelivery")
                .tag("event_type", "AUCTION_ENDED")
                .counter()
                .count())
        .isEqualTo(2.0);
  }
}
