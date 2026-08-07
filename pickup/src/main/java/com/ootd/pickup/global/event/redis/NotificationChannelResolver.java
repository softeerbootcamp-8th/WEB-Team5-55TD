package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.NotificationEvent;
import org.springframework.stereotype.Component;

/**
 * 알림 이벤트가 나가고 들어올 Redis Pub/Sub 채널 이름을 정한다.
 *
 * <p>채널 이름에 프로파일을 넣지 않는다. {@code REDIS_HOST}/{@code REDIS_PORT}가 이미 환경별로 주입되므로(각 환경이 서로 다른 Redis
 * 인스턴스를 바라본다) 같은 Redis를 여러 환경이 공유하는 상황을 전제할 필요가 없다.
 *
 * <p>실제 채널 이름과 구독 패턴이 같은 포맷({@link #CHANNEL_FORMAT})에서 파생되도록 {@link #channelOf}로 묶어 둔다. 두 리터럴로 나뉘어
 * 있으면 한쪽만 고치고 다른 쪽을 놓쳐 구독 패턴이 실제 발행 채널과 조용히 어긋날 수 있다.
 *
 * <p>{@link NotificationEvent#aggregateId()}가 {@code null}이면 즉시 예외를 던진다. {@code
 * String.valueOf(null)}은 예외 없이 문자열 {@code "null"}을 반환하는데, 이걸 그대로 두면 {@code
 * pickup:notification:AUCTION:null} 같은 채널로 조용히 발행되고 만다 — 아무도 구독하지 않는 채널로 알림이 새는데도 아무 신호가 없다. {@code
 * aggregateId()}는 계약상 항상 있어야 하는 값이라 여기서 막는 게 문제를 발생 지점 가장 가까이에서 드러낸다.
 */
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
