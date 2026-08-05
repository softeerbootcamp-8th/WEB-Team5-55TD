package com.ootd.pickup.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "slackNotificationExecutor")
  public Executor slackNotificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("slack-notify-");
    executor.initialize();
    return executor;
  }

  /**
   * 입찰 기록(추월 처리 + Bid 저장)을 단일 스레드로 직렬 처리한다. 스레드를 여러 개 두면 제출 순서와 실행 순서가 달라질 수 있어, 같은 경매에 대한 기록 순서가
   * 실제 현재가 갱신(커밋) 순서와 어긋날 수 있다.
   */
  @Bean(name = "bidRecordingExecutor")
  public Executor bidRecordingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("bid-record-");
    executor.initialize();
    return executor;
  }
}
