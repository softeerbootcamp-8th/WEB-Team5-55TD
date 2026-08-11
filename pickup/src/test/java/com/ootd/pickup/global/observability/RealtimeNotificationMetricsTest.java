package com.ootd.pickup.global.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.global.event.EventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class RealtimeNotificationMetricsTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RealtimeNotificationMetrics metrics =
      new RealtimeNotificationMetrics(meterRegistry);

  @Test
  void Redis_발행_결과를_이벤트_타입과_함께_기록한다() {
    metrics.recordRedisPublishSuccess(EventType.AUCTION_BID_UPDATED);
    metrics.recordRedisPublishFailure(EventType.AUCTION_BID_UPDATED);
    metrics.recordRedisPublishNoSubscribers(EventType.AUCTION_BID_UPDATED);
    metrics.recordRedisPublishRejected(EventType.AUCTION_BID_UPDATED);

    assertThat(count("pickup.redis.notification.publish", "success", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
    assertThat(count("pickup.redis.notification.publish", "failure", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
    assertThat(count("pickup.redis.notification.publish", "no_subscribers", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
    assertThat(count("pickup.redis.notification.publish", "rejected", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
  }

  @Test
  void Redis_수신_결과를_고정된_outcome으로_기록한다() {
    metrics.recordRedisReceiveSuccess(EventType.AUCTION_BID_UPDATED);
    metrics.recordRedisReceiveDeserializeFailure();
    metrics.recordRedisReceiveChannelMismatch(EventType.AUCTION_BID_UPDATED);

    assertThat(count("pickup.redis.notification.receive", "success", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
    assertThat(count("pickup.redis.notification.receive", "deserialize_failure", "unknown"))
        .isEqualTo(1);
    assertThat(
            count("pickup.redis.notification.receive", "channel_mismatch", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
  }

  @Test
  void Broker_발행_결과를_이벤트_타입과_함께_기록한다() {
    metrics.recordBrokerPublishSuccess(EventType.AUCTION_BID_UPDATED);
    metrics.recordBrokerPublishFailure(EventType.AUCTION_BID_UPDATED);

    assertThat(count("pickup.websocket.broker.publish", "success", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
    assertThat(count("pickup.websocket.broker.publish", "failure", "AUCTION_BID_UPDATED"))
        .isEqualTo(1);
  }

  private double count(String metricName, String outcome, String eventType) {
    return meterRegistry
        .get(metricName)
        .tags("outcome", outcome, "event_type", eventType)
        .counter()
        .count();
  }
}
