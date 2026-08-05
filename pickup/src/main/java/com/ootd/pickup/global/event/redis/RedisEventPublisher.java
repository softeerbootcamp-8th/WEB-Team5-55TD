package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import org.springframework.stereotype.Component;

@Component
public class RedisEventPublisher implements EventPublisher {

  @Override
  public void publish(NotificationEvent event) {}
}
