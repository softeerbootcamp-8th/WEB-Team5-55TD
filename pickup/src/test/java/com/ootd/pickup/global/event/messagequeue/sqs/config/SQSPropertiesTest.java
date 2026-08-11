package com.ootd.pickup.global.event.messagequeue.sqs.config;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SQSPropertiesTest {

  private static final String FIFO_QUEUE_URL =
      "https://sqs.ap-northeast-2.amazonaws.com/123456789012/pickup-event.fifo";

  @Test
  void 표준_큐를_가리키면_예외가_발생한다() {
    // given — MessageGroupId 는 FIFO 큐에만 있어 표준 큐면 전송이 매번 거부된다
    String standardQueueUrl = "https://sqs.ap-northeast-2.amazonaws.com/123456789012/pickup-event";

    // when & then
    assertThatThrownBy(() -> properties(standardQueueUrl, Duration.ofSeconds(20)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FIFO");
  }

  @Test
  void 큐_URL이_비어_있으면_FIFO가_아니라고_알리지_않는다() {
    // given — 환경변수를 아예 넣지 않은 경우다. @NotBlank 가 알려야 원인을 찾을 수 있다

    // when & then
    assertThatCode(() -> properties("", Duration.ofSeconds(20))).doesNotThrowAnyException();
  }

  @Test
  void 롱_폴링_대기가_0이면_예외가_발생한다() {
    // given — 빈 응답이 즉시 돌아와 폴링 루프가 쉬지 않고 돈다

    // when & then
    assertThatThrownBy(() -> properties(FIFO_QUEUE_URL, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wait-time");
  }

  @Test
  void 롱_폴링_대기가_1초_미만이면_예외가_발생한다() {
    // given — toSeconds() 로 잘려 0 이 되므로 0 을 준 것과 같아진다

    // when & then
    assertThatThrownBy(() -> properties(FIFO_QUEUE_URL, Duration.ofMillis(500)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wait-time");
  }

  @Test
  void 롱_폴링_대기가_20초를_넘으면_예외가_발생한다() {
    // given — SQS 상한이다

    // when & then
    assertThatThrownBy(() -> properties(FIFO_QUEUE_URL, Duration.ofSeconds(21)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wait-time");
  }

  @Test
  void 가시성_제한_시간이_1초_미만이면_예외가_발생한다() {
    // given — 0 이면 받은 메시지가 즉시 다시 보여 같은 이벤트가 중복 처리된다

    // when & then
    assertThatThrownBy(
            () ->
                new SQSProperties(
                    FIFO_QUEUE_URL,
                    "ap-northeast-2",
                    Duration.ofSeconds(20),
                    Duration.ofMillis(500),
                    10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("visibility-timeout");
  }

  @Test
  void 상한과_하한_안의_값은_통과한다() {
    // given & when & then
    assertThatCode(() -> properties(FIFO_QUEUE_URL, Duration.ofSeconds(1)))
        .doesNotThrowAnyException();
    assertThatCode(() -> properties(FIFO_QUEUE_URL, Duration.ofSeconds(20)))
        .doesNotThrowAnyException();
  }

  private SQSProperties properties(String queueUrl, Duration waitTime) {
    return new SQSProperties(queueUrl, "ap-northeast-2", waitTime, Duration.ofSeconds(30), 10);
  }
}
