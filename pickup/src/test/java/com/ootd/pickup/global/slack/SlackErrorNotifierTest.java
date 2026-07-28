package com.ootd.pickup.global.slack;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SlackErrorNotifierTest {

    private static final String SLACK_API_BASE_URL = "https://slack.com/api";

    private SlackErrorNotifier createNotifier(RestClient restClient, SlackProperties properties) {
        Environment environment = Mockito.mock(Environment.class);
        Mockito.when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        return new SlackErrorNotifier(restClient, properties, environment);
    }

    private ErrorRequestContext createContext() {
        return new ErrorRequestContext("GET", "/api/test", null, "127.0.0.1", LocalDateTime.now());
    }

    @Nested
    class 알림_전송 {

        @Test
        void 활성화_상태이면_챗포스트메시지_API를_호출한다() {
            // given
            RestClient.Builder builder = RestClient.builder()
                .baseUrl(SLACK_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test-token");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            SlackProperties properties = new SlackProperties(true, "xoxb-test-token", "pickup-error-dev");
            SlackErrorNotifier notifier = createNotifier(restClient, properties);

            server.expect(requestTo(SLACK_API_BASE_URL + "/chat.postMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer xoxb-test-token"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

            // when
            notifier.notifyError(new RuntimeException("테스트 예외"), createContext());

            // then
            server.verify();
        }

        @Test
        void 비활성화_상태이면_요청을_보내지_않는다() {
            // given
            RestClient.Builder builder = RestClient.builder().baseUrl(SLACK_API_BASE_URL);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            SlackProperties properties = new SlackProperties(false, "xoxb-test-token", "pickup-error-dev");
            SlackErrorNotifier notifier = createNotifier(restClient, properties);

            // when
            notifier.notifyError(new RuntimeException("테스트 예외"), createContext());

            // then
            server.verify();
        }

        @Test
        void 봇토큰이_없으면_요청을_보내지_않는다() {
            // given
            RestClient.Builder builder = RestClient.builder().baseUrl(SLACK_API_BASE_URL);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            SlackProperties properties = new SlackProperties(true, "", "pickup-error-dev");
            SlackErrorNotifier notifier = createNotifier(restClient, properties);

            // when
            notifier.notifyError(new RuntimeException("테스트 예외"), createContext());

            // then
            server.verify();
        }

        @Test
        void 슬랙_응답이_실패여도_예외를_전파하지_않는다() {
            // given
            RestClient.Builder builder = RestClient.builder()
                .baseUrl(SLACK_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test-token");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            SlackProperties properties = new SlackProperties(true, "xoxb-test-token", "pickup-error-dev");
            SlackErrorNotifier notifier = createNotifier(restClient, properties);

            server.expect(requestTo(SLACK_API_BASE_URL + "/chat.postMessage"))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"channel_not_found\"}", MediaType.APPLICATION_JSON));

            // when & then
            notifier.notifyError(new RuntimeException("테스트 예외"), createContext());
            server.verify();
        }
    }

    @Nested
    class 활성_프로필_설정 {

        @Test
        void 활성_프로필이_있으면_활성_프로필명이_포함된다() {
            // given
            RestClient.Builder builder = RestClient.builder()
                .baseUrl(SLACK_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test-token");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            Environment environment = Mockito.mock(Environment.class);
            Mockito.when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

            SlackProperties properties = new SlackProperties(true, "xoxb-test-token", "pickup-error-dev");
            SlackErrorNotifier notifier = new SlackErrorNotifier(restClient, properties, environment);

            server.expect(requestTo(SLACK_API_BASE_URL + "/chat.postMessage"))
                .andExpect(content().string(containsString("prod")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

            // when
            notifier.notifyError(new RuntimeException("테스트 예외"), createContext());

            // then
            server.verify();
        }

        @Test
        void 활성_프로필이_없으면_기본_프로필명이_포함된다() {
            // given
            RestClient.Builder builder = RestClient.builder()
                .baseUrl(SLACK_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test-token");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient restClient = builder.build();

            Environment environment = Mockito.mock(Environment.class);
            Mockito.when(environment.getActiveProfiles()).thenReturn(new String[0]);
            Mockito.when(environment.getDefaultProfiles()).thenReturn(new String[] {"dev"});

            SlackProperties properties = new SlackProperties(true, "xoxb-test-token", "pickup-error-dev");
            SlackErrorNotifier notifier = new SlackErrorNotifier(restClient, properties, environment);

            server.expect(requestTo(SLACK_API_BASE_URL + "/chat.postMessage"))
                .andExpect(content().string(containsString("dev")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

            // when
            notifier.notifyError(new RuntimeException("테스트 예외"), createContext());

            // then
            server.verify();
        }
    }
}
