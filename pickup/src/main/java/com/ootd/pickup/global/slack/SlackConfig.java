package com.ootd.pickup.global.slack;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SlackProperties.class)
public class SlackConfig {

    @Bean
    RestClient slackRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
