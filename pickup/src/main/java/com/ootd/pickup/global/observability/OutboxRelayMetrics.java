package com.ootd.pickup.global.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 릴레이의 대기행렬 지연을 드러내는 지표.
 *
 * <p>{@code Sqs.SendMessage}/{@code Sqs.ReceiveMessage} 같은 스팬은 호출 자체의 소요 시간만 잰다 — 적체 상황에서도 큐가 한 번도
 * 비지 않으니 개별 호출은 계속 빠르게 보이고, 정작 느려지는 원인인 "이벤트가 아웃박스 테이블에서 자기 차례를 기다린 시간"은 어떤 스팬에도 잡히지 않는다. 이 클래스는 그
 * 대기 시간을 명시적으로 측정해 트레이스가 놓치는 지연을 지표로 보이게 한다.
 */
@Component
public class OutboxRelayMetrics {

  private static final String EVENT_AGE = "pickup.outbox.event.age";
  private static final String PENDING_COUNT = "pickup.outbox.pending.count";

  private final Timer eventAgeTimer;
  private final AtomicLong pendingCount = new AtomicLong();

  public OutboxRelayMetrics(MeterRegistry meterRegistry) {
    this.eventAgeTimer =
        Timer.builder(EVENT_AGE)
            .description("적재 시각(occurredAt)부터 실제 SQS 전송 시각까지 — 대기행렬에서 기다린 시간")
            .publishPercentileHistogram()
            .register(meterRegistry);
    Gauge.builder(PENDING_COUNT, pendingCount, AtomicLong::get)
        .description("발행을 기다리는 outbox_event 전체 행 수 — 큐 깊이")
        .register(meterRegistry);
  }

  /** 이벤트 하나가 적재된 뒤 실제로 전송되기까지 대기행렬에서 기다린 시간을 기록한다. */
  public void recordEventAge(Duration age) {
    eventAgeTimer.record(age);
  }

  /** 이번 조회 시점의 발행 대기 전체 건수로 큐 깊이 게이지를 갱신한다. */
  public void recordPendingCount(long count) {
    pendingCount.set(count);
  }
}
