package com.ootd.pickup.global.slack;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SlackProperties.class)
public class SlackConfig {

    private static final String SLACK_API_BASE_URL = "https://slack.com/api";

    @Bean
    RestClient slackRestClient(RestClient.Builder builder, SlackProperties slackProperties) {
        builder.baseUrl(SLACK_API_BASE_URL);
        if (StringUtils.hasText(slackProperties.botToken())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + slackProperties.botToken());
        }
        return builder.build();
    }
}
