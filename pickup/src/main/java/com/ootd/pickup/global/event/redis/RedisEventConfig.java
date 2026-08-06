package com.ootd.pickup.global.event.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

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
