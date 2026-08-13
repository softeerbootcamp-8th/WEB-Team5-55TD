package com.ootd.pickup.auth.kakao;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoConfig {
  @Bean
  KakaoClient kakaoClient(RestClient.Builder builder, KakaoProperties properties) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.timeout());
    return new KakaoClient(builder.requestFactory(requestFactory).build(), properties);
  }
}
