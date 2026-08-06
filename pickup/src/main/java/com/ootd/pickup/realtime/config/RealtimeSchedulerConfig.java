package com.ootd.pickup.realtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/*
웹소켓 연결 하트비트를 일정 주기로 실행할 전용 스케줄러를 스프링 빈으로 만드는 설정
 */
@Configuration
public class RealtimeSchedulerConfig {

  @Bean
  public TaskScheduler realtimeHeartBeatTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("realtimeHeartBeat-");
    return scheduler;
  }
}
