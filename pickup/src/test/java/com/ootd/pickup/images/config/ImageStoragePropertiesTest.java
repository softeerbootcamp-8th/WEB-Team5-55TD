package com.ootd.pickup.images.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ImageStoragePropertiesTest {

  @Test
  void 업로드_URL_유효시간은_0보다_커야한다() {
    assertThatThrownBy(
            () ->
                new ImageStorageProperties(
                    "pickup-test", "ap-northeast-2", "https://images.test", Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 업로드_URL_유효시간은_7일을_초과할_수_없다() {
    assertThatThrownBy(
            () ->
                new ImageStorageProperties(
                    "pickup-test",
                    "ap-northeast-2",
                    "https://images.test",
                    Duration.ofDays(7).plusNanos(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 업로드_URL_유효시간이_5분이면_허용한다() {
    assertThatCode(
            () ->
                new ImageStorageProperties(
                    "pickup-test", "ap-northeast-2", "https://images.test", Duration.ofMinutes(5)))
        .doesNotThrowAnyException();
  }
}
