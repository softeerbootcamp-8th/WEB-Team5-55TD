package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.event.NotificationEventDispatcher;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventSubscriber implements MessageListener {

  private final NotificationEnvelopeReader envelopeReader;
  private final NotificationChannelResolver channelResolver;
  private final NotificationEventDispatcher eventDispatcher;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
    NotificationEvent event;
    try {
      event = envelopeReader.read(message.getBody());
    } catch (JacksonException exception) {
      log.warn("알림 이벤트 역직렬화에 실패했습니다 - channel={}", channel, exception);
      return;
    }
    if (event == null) {
      return;
    }
    if (!channelResolver.matches(channel, event)) {
      log.warn(
          "Redis 채널과 알림 이벤트 대상이 일치하지 않습니다 - channel={}, eventType={}, aggregateId={}",
          channel,
          event.eventType(),
          event.aggregateId());
      return;
    }
    eventDispatcher.dispatch(event);
  }
}
