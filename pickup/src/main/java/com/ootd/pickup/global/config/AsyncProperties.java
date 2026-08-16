package com.ootd.pickup.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("async")
public record AsyncProperties(Pool defaultPool, Pool slackNotification, Pool notificationEvent) {

  public record Pool(int corePoolSize, int maxPoolSize, int queueCapacity) {}
}
