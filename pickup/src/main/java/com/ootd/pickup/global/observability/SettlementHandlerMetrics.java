package com.ootd.pickup.global.observability;

import com.ootd.pickup.auction.domain.AuctionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code SettlementEventHandler}의 처리 시간을 재는 지표.
 *
 * <p>SQS {@code visibility-timeout}(현재 30초, {@code SQSProperties} 참고)은 이 핸들러의 처리 시간보다 길어야 한다. 그 값이
 * 실측 없이 임의로 고른 값이었기 때문에({@code docs/SQS_가시성_타임아웃_실측_실행_계획.md}), 재산정에 쓸 p50/p95/p99 분포를 여기서 모은다.
 *
 * <p>{@code auctionId}/{@code eventId}처럼 값이 계속 늘어나는 필드는 태그로 쓰지 않는다({@link
 * RealtimeNotificationMetrics}와 같은 이유 — Datadog 커스텀 메트릭 카디널리티 비용). {@code
 * auctionStatus}(WON/PASSED)와 {@code outcome}(success/failure)만 태그로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SettlementHandlerMetrics {

  private static final String HANDLE_DURATION = "pickup.settlement.handler.duration";

  private final MeterRegistry meterRegistry;

  /**
   * 정산 이벤트 처리 소요 시간을 기록한다.
   *
   * <p>다른 인스턴스가 동시에 처리해 유니크 제약에 막힌 경우({@code DataIntegrityViolationException})도 {@code
   * success=true}로 기록한다. 실패가 아니라 이미 다른 인스턴스가 같은 정산을 끝냈다는 정상 소비 신호이기 때문이다({@link
   * com.ootd.pickup.settlement.handler.SettlementEventHandler} 참고).
   */
  public void recordDuration(AuctionStatus auctionStatus, boolean success, Duration elapsed) {
    Timer.builder(HANDLE_DURATION)
        .tag("auction_status", auctionStatus.name())
        .tag("outcome", success ? "success" : "failure")
        .publishPercentileHistogram()
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)
        .record(elapsed);
  }
}
