package com.ootd.pickup.cards.sync.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TcgdexProperties.class)
public class TcgdexClientConfig {

  @Bean
  RestClient tcgdexApiRestClient(RestClient.Builder builder, TcgdexProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(properties.timeout());
    return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }
}
