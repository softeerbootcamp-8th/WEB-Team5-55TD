package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RedisEventPublisher implements EventPublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final NotificationChannelResolver channelResolver;

  @Override
  public void publish(NotificationEvent event) {
    redisTemplate.convertAndSend(channelResolver.resolve(event), serialize(event));
  }

  private String serialize(NotificationEvent event) {
    JsonNode payload = objectMapper.valueToTree(event);
    return objectMapper.writeValueAsString(new NotificationEnvelope(event.eventType(), payload));
  }
}
