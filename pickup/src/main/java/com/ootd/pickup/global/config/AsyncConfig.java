package com.ootd.pickup.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfig {

  private final AsyncProperties properties;

  public AsyncConfig(AsyncProperties properties) {
    this.properties = properties;
  }

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    AsyncProperties.Pool pool = properties.defaultPool();
    executor.setCorePoolSize(pool.corePoolSize());
    executor.setMaxPoolSize(pool.maxPoolSize());
    executor.setQueueCapacity(pool.queueCapacity());
    executor.setThreadNamePrefix("default-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = "slackNotificationExecutor")
  public Executor slackNotificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    AsyncProperties.Pool pool = properties.slackNotification();
    executor.setCorePoolSize(pool.corePoolSize());
    executor.setMaxPoolSize(pool.maxPoolSize());
    executor.setQueueCapacity(pool.queueCapacity());
    executor.setThreadNamePrefix("slack-notify-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean(name = "notificationEventExecutor")
  public Executor notificationEventExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    AsyncProperties.Pool pool = properties.notificationEvent();
    executor.setCorePoolSize(pool.corePoolSize());
    executor.setMaxPoolSize(pool.maxPoolSize());
    executor.setQueueCapacity(pool.queueCapacity());
    executor.setThreadNamePrefix("notification-event-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
