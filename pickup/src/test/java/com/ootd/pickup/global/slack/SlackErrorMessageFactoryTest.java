package com.ootd.pickup.global.slack;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SlackErrorMessageFactoryTest {

  @Test
  void 페이로드를_생성하면_채널과_블록이_포함된다() {
    // given
    RuntimeException exception = new IllegalStateException("테스트 예외");
    ErrorRequestContext context =
        new ErrorRequestContext(
            "GET", "/api/test", "id=1", "127.0.0.1", LocalDateTime.of(2026, 7, 28, 12, 0));

    // when
    Map<String, Object> payload =
        SlackErrorMessageFactory.buildPayload(exception, context, "dev", "pickup-error-dev");

    // then
    assertThat(payload.get("channel")).isEqualTo("pickup-error-dev");
    assertThat(payload.get("blocks")).isInstanceOf(List.class);
    assertThat((List<?>) payload.get("blocks")).hasSize(5);
  }

  @Test
  void 헤더_제목에_활성_프로필이_대문자_접두어로_포함된다() {
    // given
    RuntimeException exception = new IllegalStateException("테스트 예외");
    ErrorRequestContext context =
        new ErrorRequestContext(
            "GET", "/api/test", null, "127.0.0.1", LocalDateTime.of(2026, 7, 28, 12, 0));

    // when
    Map<String, Object> payload =
        SlackErrorMessageFactory.buildPayload(exception, context, "dev", "pickup-error-dev");

    // then
    List<?> blocks = (List<?>) payload.get("blocks");
    Map<?, ?> headerBlock = (Map<?, ?>) blocks.get(0);
    Map<?, ?> text = (Map<?, ?>) headerBlock.get("text");
    assertThat((String) text.get("text")).startsWith("[DEV]");
  }

  @Test
  void 예외메시지가_없으면_기본_문구가_포함된다() {
    // given
    RuntimeException exception = new IllegalStateException();
    ErrorRequestContext context =
        new ErrorRequestContext(
            "GET", "/api/test", null, "127.0.0.1", LocalDateTime.of(2026, 7, 28, 12, 0));

    // when
    Map<String, Object> payload =
        SlackErrorMessageFactory.buildPayload(exception, context, "dev", "pickup-error-dev");

    // then
    List<?> blocks = (List<?>) payload.get("blocks");
    Map<?, ?> messageBlock = (Map<?, ?>) blocks.get(3);
    Map<?, ?> text = (Map<?, ?>) messageBlock.get("text");
    assertThat((String) text.get("text")).contains("(메시지 없음)");
  }
}
