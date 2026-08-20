package com.ootd.pickup.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.AuctionStatus;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class SettlementHandlerMetricsTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final SettlementHandlerMetrics metrics = new SettlementHandlerMetrics(meterRegistry);

  @Test
  void 경매_상태와_성공_여부를_태그로_소요시간을_기록한다() {
    // when
    metrics.recordDuration(AuctionStatus.WON, true, Duration.ofMillis(120));

    // then
    assertThat(timerCount(AuctionStatus.WON, true)).isEqualTo(1);
    assertThat(totalTimeMillis(AuctionStatus.WON, true)).isCloseTo(120.0, Offset.offset(5.0));
  }

  @Test
  void 실패한_처리는_failure_태그로_구분해_기록한다() {
    // when
    metrics.recordDuration(AuctionStatus.WON, false, Duration.ofMillis(50));

    // then
    assertThat(timerCount(AuctionStatus.WON, false)).isEqualTo(1);
    assertThat(findTimer(AuctionStatus.WON, true)).isNull();
  }

  @Test
  void 유찰과_낙찰의_소요시간을_서로_다른_태그로_구분해_기록한다() {
    // when
    metrics.recordDuration(AuctionStatus.PASSED, true, Duration.ofMillis(10));
    metrics.recordDuration(AuctionStatus.WON, true, Duration.ofMillis(200));

    // then
    assertThat(timerCount(AuctionStatus.PASSED, true)).isEqualTo(1);
    assertThat(timerCount(AuctionStatus.WON, true)).isEqualTo(1);
  }

  private long timerCount(AuctionStatus auctionStatus, boolean success) {
    return meterRegistry
        .get("pickup.settlement.handler.duration")
        .tags("auction_status", auctionStatus.name(), "outcome", success ? "success" : "failure")
        .timer()
        .count();
  }

  private double totalTimeMillis(AuctionStatus auctionStatus, boolean success) {
    return meterRegistry
        .get("pickup.settlement.handler.duration")
        .tags("auction_status", auctionStatus.name(), "outcome", success ? "success" : "failure")
        .timer()
        .totalTime(TimeUnit.MILLISECONDS);
  }

  private Timer findTimer(AuctionStatus auctionStatus, boolean success) {
    return meterRegistry
        .find("pickup.settlement.handler.duration")
        .tags("auction_status", auctionStatus.name(), "outcome", success ? "success" : "failure")
        .timer();
  }
}
