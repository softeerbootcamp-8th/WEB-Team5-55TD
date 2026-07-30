package com.ootd.pickup.global.slack;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

class SlackConfigTest {

  private final SlackConfig slackConfig = new SlackConfig();

  @Test
  void 봇토큰이_있으면_Authorization_헤더를_설정한다() {
    // given
    RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_SELF);
    SlackProperties properties = new SlackProperties(true, "xoxb-test-token", "pickup-error-dev");

    // when
    slackConfig.slackRestClient(builder, properties);

    // then
    verify(builder).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer xoxb-test-token");
  }

  @Test
  void 봇토큰이_없으면_Authorization_헤더를_설정하지_않는다() {
    // given
    RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_SELF);
    SlackProperties properties = new SlackProperties(true, "", "pickup-error-dev");

    // when
    slackConfig.slackRestClient(builder, properties);

    // then
    verify(builder, never()).defaultHeader(anyString(), anyString());
  }
}
