package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import org.springframework.stereotype.Component;

/**
 * 알림 이벤트를 Redis Pub/Sub 채널로 발행하는 {@link EventPublisher} 구현체.
 *
 * <p><b>아직 비어 있어 발행해도 아무 일도 일어나지 않는다.</b> 알림은 유실이 허용되는 계열이라 호출자가 실패를 알 수 없다.
 */
@Component
public class RedisEventPublisher implements EventPublisher {

  @Override
  public void publish(NotificationEvent event) {
    // TODO: aggregateType 과 aggregateId 로 채널 이름을 만들어 발행한다
  }
}
