package com.ootd.pickup.global.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SlackErrorNotifier {

    private final RestClient slackRestClient;
    private final SlackProperties slackProperties;
    private final String activeProfile;

    public SlackErrorNotifier(RestClient slackRestClient, SlackProperties slackProperties, Environment environment) {
        this.slackRestClient = slackRestClient;
        this.slackProperties = slackProperties;
        this.activeProfile = String.join(",", environment.getActiveProfiles());
    }

    @Async("slackNotificationExecutor")
    public void notifyError(RuntimeException exception, ErrorRequestContext context) {
        if (!slackProperties.enabled() || !StringUtils.hasText(slackProperties.webhookUrl())) {
            return;
        }

        try {
            Map<String, Object> payload = SlackErrorMessageFactory.buildPayload(exception, context, activeProfile);
            slackRestClient.post()
                    .uri(slackProperties.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception sendFailure) {
            log.error("Slack 에러 알림 전송에 실패했습니다.", sendFailure);
        }
    }
}
