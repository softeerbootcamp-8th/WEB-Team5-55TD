package com.ootd.pickup.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class RealtimeWebSocketPropertiesTest {

  @Test
  void localhost와_CloudFront_Origin을_허용한다() {
    try (ConfigurableApplicationContext context = createContext("dev")) {
      RealtimeWebSocketProperties properties = context.getBean(RealtimeWebSocketProperties.class);

      assertThat(properties.allowedOrigins())
          .containsExactly("http://localhost:5173", "https://d38qqh1zkovln3.cloudfront.net");
    }
  }

  private ConfigurableApplicationContext createContext(String profile) {
    return new SpringApplicationBuilder(TestConfiguration.class)
        .web(WebApplicationType.NONE)
        .profiles(profile)
        .properties("spring.main.banner-mode=off", "spring.main.log-startup-info=false")
        .run();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(RealtimeWebSocketProperties.class)
  static class TestConfiguration {}
}
