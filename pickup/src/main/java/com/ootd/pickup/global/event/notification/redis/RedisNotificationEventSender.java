package com.ootd.pickup.global.event.notification.redis;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.event.notification.NotificationEventSender;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림 이벤트를 Redis Pub/Sub 채널로 보내는 {@link NotificationEventSender} 구현체.
 *
 * <p>{@link NotificationEventSender} 계약대로, 이 메서드는 호출 시점이 이미 트랜잭션 커밋 이후라는 전제로 짜여 있다 — 여기서 커밋 여부를 다시
 * 확인하지 않는다. 그 전제는 {@link EventPublisher}가 지킨다.
 *
 * <p>직렬화 실패와 전송 실패는 잡지 않고 그대로 던진다. 호출자가 받아 로그로 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNotificationEventSender implements NotificationEventSender {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final RedisChannelResolver channelResolver;
  private final RealtimeNotificationMetrics metrics;

  @Override
  public void send(NotificationEvent event) {
    try {
      Long subscriberCount =
          redisTemplate.convertAndSend(channelResolver.resolve(event), serialize(event));
      // Redis PUBLISH는 구독자가 없어도 예외 없이 성공하므로 반환값을 확인해야 실제 알림 유실을 발견할 수 있다.
      if (subscriberCount == 0) {
        metrics.recordRedisPublishNoSubscribers(event.eventType());
        log.debug(
            "구독자가 없어 알림 이벤트가 전달되지 않았습니다 - eventType={}, aggregateId={}",
            event.eventType(),
            event.aggregateId());
        return;
      }
      metrics.recordRedisPublishSuccess(event.eventType());
      log.debug(
          "Redis 채널로 알림 이벤트를 발행했습니다 - eventType={}, aggregateId={}, subscriberCount={}",
          event.eventType(),
          event.aggregateId(),
          subscriberCount);
    } catch (RuntimeException exception) {
      metrics.recordRedisPublishFailure(event.eventType());
      throw exception;
    }
  }

  private String serialize(NotificationEvent event) {
    JsonNode payload = objectMapper.valueToTree(event);
    return objectMapper.writeValueAsString(new RedisEnvelope(event.eventType(), payload));
  }
}
