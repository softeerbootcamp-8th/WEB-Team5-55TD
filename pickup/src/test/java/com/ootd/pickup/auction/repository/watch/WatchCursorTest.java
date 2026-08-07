package com.ootd.pickup.auction.repository.watch;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class WatchCursorTest {

  @Test
  void 인코딩한_커서를_디코딩하면_원래_값을_반환한다() {
    // given
    String encoded = WatchCursor.encode(5L);

    // when
    Long watchId = WatchCursor.decode(encoded);

    // then
    assertThat(watchId).isEqualTo(5L);
  }

  @Test
  void 커서가_없으면_null을_반환한다() {
    assertThat(WatchCursor.decode(null)).isNull();
    assertThat(WatchCursor.decode("  ")).isNull();
  }

  @Test
  void 뒤에_구분자가_붙은_커서면_예외가_발생한다() {
    // given
    String cursorWithTrailingDelimiter = "MXw"; // base64url of "1|"

    // when & then
    assertThatThrownBy(() -> WatchCursor.decode(cursorWithTrailingDelimiter))
        .isInstanceOf(PickUpException.class);
  }
}
