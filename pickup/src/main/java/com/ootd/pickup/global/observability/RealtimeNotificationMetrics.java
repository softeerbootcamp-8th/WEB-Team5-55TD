package com.ootd.pickup.global.observability;

import com.ootd.pickup.global.event.EventType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 다중 인스턴스 알림이 Redis 발행 → 인스턴스별 수신 → WebSocket Broker 전달 중 어디서 끊겼는지 비교하기 위한 지표 모음.
 *
 * <p>지표 이름과 태그를 한곳에서 관리해야 경계마다 이름이 달라지는 일을 막고, ID처럼 값이 계속 늘어나는 태그가 추가되어 Datadog 시계열 비용이 커지는 것도 방지할
 * 수 있다.
 */
@Component
@RequiredArgsConstructor
public class RealtimeNotificationMetrics {

  private static final String REDIS_PUBLISH = "pickup.redis.notification.publish";
  private static final String REDIS_RECEIVE = "pickup.redis.notification.receive";
  private static final String BROKER_PUBLISH = "pickup.websocket.broker.publish";
  private static final String UNKNOWN_EVENT_TYPE = "unknown";

  private final MeterRegistry meterRegistry;

  public void recordRedisPublishSuccess(EventType eventType) {
    increment(REDIS_PUBLISH, "success", eventType.name());
  }

  public void recordRedisPublishFailure(EventType eventType) {
    increment(REDIS_PUBLISH, "failure", eventType.name());
  }

  public void recordRedisPublishNoSubscribers(EventType eventType) {
    increment(REDIS_PUBLISH, "no_subscribers", eventType.name());
  }

  public void recordRedisPublishRejected(EventType eventType) {
    increment(REDIS_PUBLISH, "rejected", eventType.name());
  }

  public void recordRedisReceiveSuccess(EventType eventType) {
    increment(REDIS_RECEIVE, "success", eventType.name());
  }

  public void recordRedisReceiveDeserializeFailure() {
    // 역직렬화 전 payload의 eventType은 신뢰할 수 없으므로 유한한 값인 unknown으로 고정한다.
    increment(REDIS_RECEIVE, "deserialize_failure", UNKNOWN_EVENT_TYPE);
  }

  public void recordRedisReceiveChannelMismatch(EventType eventType) {
    increment(REDIS_RECEIVE, "channel_mismatch", eventType.name());
  }

  public void recordBrokerPublishSuccess(EventType eventType) {
    increment(BROKER_PUBLISH, "success", eventType.name());
  }

  public void recordBrokerPublishFailure(EventType eventType) {
    increment(BROKER_PUBLISH, "failure", eventType.name());
  }

  private void increment(String metricName, String outcome, String eventType) {
    meterRegistry.counter(metricName, "outcome", outcome, "event_type", eventType).increment();
  }
}
