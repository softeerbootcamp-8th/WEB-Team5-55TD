package com.ootd.pickup.global.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** SQS 메시지와 배치의 실제 소비 시간을 기록하는 지표. */
@Component
@RequiredArgsConstructor
public class SQSEventConsumerMetrics {

  private static final String MESSAGE_DURATION = "pickup.sqs.consumer.message.duration";
  private static final String BATCH_DURATION = "pickup.sqs.consumer.batch.duration";
  private static final String BATCH_SIZE = "pickup.sqs.consumer.batch.size";
  private static final String MESSAGE_REDELIVERY = "pickup.sqs.message.redelivery";

  private final MeterRegistry meterRegistry;

  /**
   * 메시지 하나를 역직렬화하고 등록된 핸들러를 모두 호출하는 데 걸린 시간을 기록한다.
   *
   * <p>{@code outcome}은 비즈니스 성공 여부가 아니라 SQS에서 삭제해도 되는 기술적 소비 성공 여부다. 입찰이 업무 규칙에 따라 거절되어도 실패 상태가 정상
   * 저장되면 메시지 소비는 성공이다.
   */
  public void recordMessageDuration(String eventType, boolean success, Duration elapsed) {
    Timer.builder(MESSAGE_DURATION)
        .tag("event_type", eventType)
        .tag("outcome", success ? "success" : "failure")
        .publishPercentileHistogram()
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)
        .record(elapsed);
  }

  /** 한 번 받은 배치를 처리하고 성공한 메시지 삭제 요청을 마칠 때까지의 시간과 크기를 기록한다. */
  public void recordBatch(BatchOutcome outcome, int batchSize, Duration elapsed) {
    Timer.builder(BATCH_DURATION)
        .tag("outcome", outcome.tagValue())
        .publishPercentileHistogram()
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)
        .record(elapsed);

    DistributionSummary.builder(BATCH_SIZE)
        .tag("outcome", outcome.tagValue())
        .register(meterRegistry)
        .record(batchSize);
  }

  /** SQS가 같은 메시지를 두 번째 이상 전달한 횟수를 사건 종류별로 기록한다. */
  public void recordRedelivery(String eventType) {
    Counter.builder(MESSAGE_REDELIVERY)
        .tag("event_type", eventType)
        .register(meterRegistry)
        .increment();
  }

  public enum BatchOutcome {
    SUCCESS("success"),
    PARTIAL_FAILURE("partial_failure"),
    FAILURE("failure");

    private final String tagValue;

    BatchOutcome(String tagValue) {
      this.tagValue = tagValue;
    }

    public String tagValue() {
      return tagValue;
    }
  }
}
