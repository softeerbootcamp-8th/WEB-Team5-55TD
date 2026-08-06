package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.NotificationEvent;
import org.springframework.stereotype.Component;

@Component
public class NotificationChannelResolver {

  private static final String CHANNEL_FORMAT = "pickup:notification:%s:%s";
  private static final String WILDCARD = "*";

  public String resolve(NotificationEvent event) {
    Long aggregateId = event.aggregateId();
    if (aggregateId == null) {
      throw new IllegalStateException(
          "aggregateId가 없는 이벤트는 채널을 만들 수 없습니다 - eventType=" + event.eventType());
    }
    return channelOf(event.aggregateType().name(), String.valueOf(aggregateId));
  }

  public String resolvePattern() {
    return channelOf(WILDCARD, WILDCARD);
  }

  public boolean matches(String channel, NotificationEvent event) {
    return resolve(event).equals(channel);
  }

  private String channelOf(String aggregateType, String aggregateId) {
    return CHANNEL_FORMAT.formatted(aggregateType, aggregateId);
  }
}
