package com.ootd.pickup.global.event.sqs.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 메시지 큐 이벤트가 오가는 SQS FIFO 큐 접속·폴링 설정.
 *
 * <p>{@code event.sqs.enabled=true} 일 때만 바인딩된다. 값이 비어 있어도 앱이 뜨는 것은 그 때문이고, 켜는 순간 아래 검증에 걸려 시작 단계에서
 * 실패한다.
 *
 * @param queueUrl 큐 URL. <b>{@code .fifo} 로 끝나야 한다</b> — {@code MessageGroupId} 와 {@code
 *     MessageDeduplicationId} 는 FIFO 큐에만 있는 값이라, 표준 큐를 가리키면 전송이 매번 거부된다
 * @param region 큐가 있는 리전
 * @param waitTime 롱 폴링 대기 시간. 0이면 빈 응답이 즉시 돌아와 폴링 루프가 쉬지 않고 돈다
 * @param visibilityTimeout 받아간 메시지가 다른 소비자에게 다시 보이지 않는 시간. <b>핸들러 처리 시간보다 길어야 한다</b> — 짧으면 처리 중인
 *     메시지가 다시 전달되어 같은 이벤트가 동시에 두 번 처리된다
 * @param maxMessages 한 번에 받아올 최대 메시지 수
 */
@Validated
@ConfigurationProperties(prefix = "event.sqs")
public record SQSProperties(
    @NotBlank String queueUrl,
    @NotBlank String region,
    @NotNull Duration waitTime,
    @NotNull Duration visibilityTimeout,
    @Min(1) @Max(10) int maxMessages) {

  /** {@code ReceiveMessage} 의 {@code WaitTimeSeconds} 상한. */
  private static final Duration MAX_WAIT_TIME = Duration.ofSeconds(20);

  /** {@code ReceiveMessage} 의 {@code VisibilityTimeout} 상한. */
  private static final Duration MAX_VISIBILITY_TIMEOUT = Duration.ofHours(12);

  public SQSProperties {
    // 빈 값은 @NotBlank 가 알리게 둔다. 여기서 먼저 걸면 URL 을 아예 넣지 않은 경우에도
    // "FIFO 큐가 아니다" 로 읽혀 환경변수 누락을 엉뚱한 곳에서 찾게 된다.
    if (queueUrl != null && !queueUrl.isBlank() && !queueUrl.endsWith(".fifo")) {
      throw new IllegalArgumentException("event.sqs.queue-url must point to a FIFO queue (.fifo)");
    }
    if (waitTime != null && (waitTime.isNegative() || waitTime.compareTo(MAX_WAIT_TIME) > 0)) {
      throw new IllegalArgumentException("event.sqs.wait-time must be between 0s and 20s");
    }
    if (visibilityTimeout != null
        && (visibilityTimeout.isNegative()
            || visibilityTimeout.compareTo(MAX_VISIBILITY_TIMEOUT) > 0)) {
      throw new IllegalArgumentException(
          "event.sqs.visibility-timeout must be between 0s and 12 hours");
    }
  }
}
