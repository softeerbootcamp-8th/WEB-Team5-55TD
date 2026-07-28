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
        String[] profiles = environment.getActiveProfiles().length > 0
            ? environment.getActiveProfiles()
            : environment.getDefaultProfiles();
        this.activeProfile = String.join(",", profiles);
    }

    @Async("slackNotificationExecutor")
    public void notifyError(RuntimeException exception, ErrorRequestContext context) {
        if (!slackProperties.enabled()
            || !StringUtils.hasText(slackProperties.botToken())
            || !StringUtils.hasText(slackProperties.channel())) {
            return;
        }

        try {
            Map<String, Object> payload = SlackErrorMessageFactory.buildPayload(
                exception, context, activeProfile, slackProperties.channel());
            SlackChatPostMessageResponse response = slackRestClient.post()
                .uri("/chat.postMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(SlackChatPostMessageResponse.class);

            if (response == null || !response.ok()) {
                String error = response == null ? "응답 없음" : response.error();
                log.warn("Slack 에러 알림 전송이 거절되었습니다 - channel={}, error={}", slackProperties.channel(), error);
            }
        } catch (Exception sendFailure) {
            log.error("Slack 에러 알림 전송에 실패했습니다 - channel={}", slackProperties.channel(), sendFailure);
        }
    }
}
