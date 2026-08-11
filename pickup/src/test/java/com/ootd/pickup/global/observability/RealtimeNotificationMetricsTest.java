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
    metrics.recordRedisPublishSuccess(EventType.BID_REQUEST_SUCCEEDED);
    metrics.recordRedisPublishFailure(EventType.BID_REQUEST_SUCCEEDED);
    metrics.recordRedisPublishNoSubscribers(EventType.BID_REQUEST_SUCCEEDED);
    metrics.recordRedisPublishRejected(EventType.BID_REQUEST_SUCCEEDED);

    assertThat(count("pickup.redis.notification.publish", "success", "BID_REQUEST_SUCCEEDED"))
        .isEqualTo(1);
    assertThat(count("pickup.redis.notification.publish", "failure", "BID_REQUEST_SUCCEEDED"))
        .isEqualTo(1);
    assertThat(
            count("pickup.redis.notification.publish", "no_subscribers", "BID_REQUEST_SUCCEEDED"))
        .isEqualTo(1);
    assertThat(count("pickup.redis.notification.publish", "rejected", "BID_REQUEST_SUCCEEDED"))
        .isEqualTo(1);
  }

  @Test
  void Redis_수신_결과를_고정된_outcome으로_기록한다() {
    metrics.recordRedisReceiveSuccess(EventType.BID_REQUEST_SUCCEEDED);
    metrics.recordRedisReceiveDeserializeFailure();
    metrics.recordRedisReceiveChannelMismatch(EventType.BID_REQUEST_SUCCEEDED);

    assertThat(count("pickup.redis.notification.receive", "success", "BID_REQUEST_SUCCEEDED"))
        .isEqualTo(1);
    assertThat(count("pickup.redis.notification.receive", "deserialize_failure", "unknown"))
        .isEqualTo(1);
    assertThat(
            count("pickup.redis.notification.receive", "channel_mismatch", "BID_REQUEST_SUCCEEDED"))
        .isEqualTo(1);
  }

  @Test
  void Broker_발행_결과를_이벤트_타입과_함께_기록한다() {
    metrics.recordBrokerPublishSuccess(EventType.BID_REQUEST_SUCCEEDED);
    metrics.recordBrokerPublishFailure(EventType.BID_REQUEST_SUCCEEDED);

    assertThat(count("pickup.websocket.broker.publish", "success", "BID_REQUEST_SUCCEEDED"))
        .isEqualTo(1);
    assertThat(count("pickup.websocket.broker.publish", "failure", "BID_REQUEST_SUCCEEDED"))
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
