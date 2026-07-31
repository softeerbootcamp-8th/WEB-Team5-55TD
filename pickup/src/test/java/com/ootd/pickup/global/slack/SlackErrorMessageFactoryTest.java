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

  @Test
  void 활성_프로필이_없으면_헤더와_필드에_기본_문구가_포함된다() {
    // given
    RuntimeException exception = new IllegalStateException("테스트 예외");
    ErrorRequestContext context =
        new ErrorRequestContext(
            "GET", "/api/test", null, "127.0.0.1", LocalDateTime.of(2026, 7, 28, 12, 0));

    // when
    Map<String, Object> payload =
        SlackErrorMessageFactory.buildPayload(exception, context, null, "pickup-error-dev");

    // then
    List<?> blocks = (List<?>) payload.get("blocks");
    Map<?, ?> headerBlock = (Map<?, ?>) blocks.get(0);
    Map<?, ?> headerText = (Map<?, ?>) headerBlock.get("text");
    assertThat((String) headerText.get("text")).startsWith("[-]");

    Map<?, ?> summaryBlock = (Map<?, ?>) blocks.get(1);
    List<?> fields = (List<?>) summaryBlock.get("fields");
    Map<?, ?> profileField = (Map<?, ?>) fields.get(1);
    assertThat((String) profileField.get("text")).contains("*프로필:*\n-");
  }

  @Test
  void 활성_프로필이_공백문자열이면_헤더와_필드에_기본_문구가_포함된다() {
    // given
    RuntimeException exception = new IllegalStateException("테스트 예외");
    ErrorRequestContext context =
        new ErrorRequestContext(
            "GET", "/api/test", null, "127.0.0.1", LocalDateTime.of(2026, 7, 28, 12, 0));

    // when
    Map<String, Object> payload =
        SlackErrorMessageFactory.buildPayload(exception, context, "   ", "pickup-error-dev");

    // then
    List<?> blocks = (List<?>) payload.get("blocks");
    Map<?, ?> headerBlock = (Map<?, ?>) blocks.get(0);
    Map<?, ?> headerText = (Map<?, ?>) headerBlock.get("text");
    assertThat((String) headerText.get("text")).startsWith("[-]");

    Map<?, ?> summaryBlock = (Map<?, ?>) blocks.get(1);
    List<?> fields = (List<?>) summaryBlock.get("fields");
    Map<?, ?> profileField = (Map<?, ?>) fields.get(1);
    assertThat((String) profileField.get("text")).contains("*프로필:*\n-");
  }

  @Test
  void 쿼리스트링이_공백이면_경로에_포함되지_않는다() {
    // given
    RuntimeException exception = new IllegalStateException("테스트 예외");
    ErrorRequestContext context =
        new ErrorRequestContext(
            "GET", "/api/test", "  ", "127.0.0.1", LocalDateTime.of(2026, 7, 28, 12, 0));

    // when
    Map<String, Object> payload =
        SlackErrorMessageFactory.buildPayload(exception, context, "dev", "pickup-error-dev");

    // then
    List<?> blocks = (List<?>) payload.get("blocks");
    Map<?, ?> summaryBlock = (Map<?, ?>) blocks.get(1);
    List<?> fields = (List<?>) summaryBlock.get("fields");
    Map<?, ?> pathField = (Map<?, ?>) fields.get(3);
    assertThat((String) pathField.get("text")).isEqualTo("*Path:*\n/api/test");
  }

  @Test
  void 스택트레이스가_짧으면_생략_문구가_없다() {
    // given
    RuntimeException exception = new IllegalStateException("짧은 스택트레이스");
    exception.setStackTrace(
        new StackTraceElement[] {new StackTraceElement("com.example.Foo", "bar", "Foo.java", 1)});
    ErrorRequestContext context =
        new ErrorRequestContext(
            "GET", "/api/test", null, "127.0.0.1", LocalDateTime.of(2026, 7, 28, 12, 0));

    // when
    Map<String, Object> payload =
        SlackErrorMessageFactory.buildPayload(exception, context, "dev", "pickup-error-dev");

    // then
    List<?> blocks = (List<?>) payload.get("blocks");
    Map<?, ?> stackTraceBlock = (Map<?, ?>) blocks.get(4);
    Map<?, ?> text = (Map<?, ?>) stackTraceBlock.get("text");
    assertThat((String) text.get("text")).doesNotContain("more)");
  }

  @Test
  void 메시지가_최대_길이를_초과하면_잘린다() {
    // given
    String longMessage = "a".repeat(3000);
    RuntimeException exception = new IllegalStateException(longMessage);
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
    assertThat((String) text.get("text")).contains("... (truncated)");
  }
}
