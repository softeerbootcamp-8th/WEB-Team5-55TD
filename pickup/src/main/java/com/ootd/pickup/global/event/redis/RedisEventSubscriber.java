package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.event.NotificationEventDispatcher;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
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
  private final RealtimeNotificationMetrics metrics;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String channel = new String(message.getChannel(), StandardCharsets.UTF_8);

    NotificationEvent event;
    try {
      event = envelopeReader.read(message.getBody());
    } catch (JacksonException exception) {
      // 전체 publish 합계가 아니라 host별 이 값을 비교해야 특정 인스턴스의 잘못된 payload 수신을 찾을 수 있다.
      metrics.recordRedisReceiveDeserializeFailure();
      log.warn("알림 이벤트 역직렬화에 실패했습니다 - channel={}", channel, exception);
      return;
    }
    if (event == null) {
      metrics.recordRedisReceiveDeserializeFailure();
      return;
    }

    if (!channelResolver.matches(channel, event)) {
      // 역직렬화 성공만으로 처리하지 않고 routing 계약까지 통과해야 정상 수신으로 본다.
      metrics.recordRedisReceiveChannelMismatch(event.eventType());
      log.warn(
          "Redis 채널과 알림 이벤트 대상이 일치하지 않습니다 - channel={}, eventType={}, aggregateId={}",
          channel,
          event.eventType(),
          event.aggregateId());
      return;
    }

    // receive 성공은 이 인스턴스가 Redis 경계를 통과했다는 뜻이며, 뒤의 Broker 전달 성공은 별도 지표로 확인한다.
    metrics.recordRedisReceiveSuccess(event.eventType());
    log.debug(
        "Redis 채널에서 알림 이벤트를 수신했습니다 - channel={}, eventType={}, aggregateId={}",
        channel,
        event.eventType(),
        event.aggregateId());
    eventDispatcher.dispatch(event);
  }
}
