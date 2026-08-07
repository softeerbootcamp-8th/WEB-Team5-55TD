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

/**
 * Redis Pub/Sub 채널을 구독해 알림 이벤트를 핸들러로 넘기는 구독자.
 *
 * <p>이 클래스의 책임은 Redis {@link Message}에서 채널 문자열을 꺼내고, 봉투 해석({@link NotificationEnvelopeReader}) →
 * 채널-이벤트 일치 검증({@link NotificationChannelResolver}) → 핸들러 디스패치({@link
 * NotificationEventDispatcher})로 이어지는 파이프라인 순서를 정하는 오케스트레이션뿐이다. 각 단계의 실제 판단은 전부 협력자에게 위임한다 — 여기서
 * {@code reservePrice} 같은 비공개 값을 거르는 판단은 하지 않는다. 그건 실제 알림 처리를 맡는 {@link
 * com.ootd.pickup.global.event.EventHandler} 구현체의 몫이다.
 */
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
