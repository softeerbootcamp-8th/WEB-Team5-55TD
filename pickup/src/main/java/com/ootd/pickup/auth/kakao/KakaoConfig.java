package com.ootd.pickup.auth.kakao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoConfig {
  @Bean
  KakaoClient kakaoClient(RestClient.Builder builder, KakaoProperties properties) {
    return new KakaoClient(builder.build(), properties);
  }
}
