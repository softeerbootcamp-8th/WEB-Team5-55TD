package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림 이벤트를 Redis Pub/Sub 채널로 발행하는 {@link EventPublisher} 구현체.
 *
 * <p>{@link EventPublisher} 계약대로, 이 메서드는 호출자(예: {@code AuctionScheduler.publishAfterCommit})가 트랜잭션
 * 커밋 이후에만 부른다는 전제로 짜여 있다 — 여기서 커밋 여부를 다시 확인하지 않는다.
 *
 * <p>직렬화 실패는 여기서 따로 잡지 않는다. Jackson 3의 예외는 unchecked라({@code OutboxEventEntity.create}와 같은 관례) 그대로
 * 던지면 호출자의 {@code catch (RuntimeException)}가 잡아 로그만 남기고 삼킨다 — 알림은 유실이 허용되는 계열이라 이중으로 감쌀 이유가 없다.
 */
@Component
@RequiredArgsConstructor
public class RedisEventPublisher implements EventPublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final NotificationChannelResolver channelResolver;
  private final RealtimeNotificationMetrics metrics;

  @Override
  public void publish(NotificationEvent event) {
    try {
      Long subscriberCount =
          redisTemplate.convertAndSend(channelResolver.resolve(event), serialize(event));
      // Redis PUBLISH는 구독자가 없어도 예외 없이 성공하므로 반환값을 확인해야 실제 알림 유실을 발견할 수 있다.
      if (subscriberCount == 0) {
        metrics.recordRedisPublishNoSubscribers(event.eventType());
        return;
      }
      metrics.recordRedisPublishSuccess(event.eventType());
    } catch (RuntimeException exception) {
      metrics.recordRedisPublishFailure(event.eventType());
      throw exception;
    }
  }

  private String serialize(NotificationEvent event) {
    JsonNode payload = objectMapper.valueToTree(event);
    return objectMapper.writeValueAsString(new NotificationEnvelope(event.eventType(), payload));
  }
}
