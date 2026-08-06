package com.ootd.pickup.global.event.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 알림 이벤트 채널을 구독하는 리스너 컨테이너를 등록한다.
 *
 * <p>{@link RedisConnectionFactory}는 {@code spring.data.redis.*} 설정으로 Spring Boot가 이미 자동 구성한 것을 그대로
 * 받아 쓴다. 여기서 새로 만들 필요가 없다.
 */
@Configuration
public class RedisEventConfig {

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      RedisEventSubscriber subscriber,
      NotificationChannelResolver channelResolver) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, new PatternTopic(channelResolver.resolvePattern()));
    return container;
  }
}
