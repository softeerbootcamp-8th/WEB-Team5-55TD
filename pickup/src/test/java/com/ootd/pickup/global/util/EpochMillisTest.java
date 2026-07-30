package com.ootd.pickup.global.util;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EpochMillisTest {

  @Test
  void LocalDateTime를_epoch_밀리초로_변환하고_다시_복원하면_같은_값이다() {
    // given
    LocalDateTime dateTime = LocalDateTime.of(2026, 8, 1, 10, 0, 0);

    // when
    long epochMilli = EpochMillis.from(dateTime);
    LocalDateTime restored = EpochMillis.toLocalDateTime(epochMilli);

    // then
    assertThat(restored).isEqualTo(dateTime);
  }
}
