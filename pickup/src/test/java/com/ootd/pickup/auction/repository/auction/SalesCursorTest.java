package com.ootd.pickup.auction.repository.auction;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class SalesCursorTest {

  @Test
  void 인코딩한_커서를_디코딩하면_원래_값을_반환한다() {
    // given
    String encoded = SalesCursor.encode(1_700_000_000_000L, 5L);

    // when
    SalesCursor cursor = SalesCursor.decode(encoded);

    // then
    assertThat(cursor.endedAtEpochMillis()).isEqualTo(1_700_000_000_000L);
    assertThat(cursor.auctionId()).isEqualTo(5L);
  }

  @Test
  void 커서가_없으면_null을_반환한다() {
    assertThat(SalesCursor.decode(null)).isNull();
    assertThat(SalesCursor.decode("  ")).isNull();
  }

  @Test
  void 잘못된_형식의_커서면_예외가_발생한다() {
    assertThatThrownBy(() -> SalesCursor.decode("not-a-valid-base64!!"))
        .isInstanceOf(PickUpException.class);
  }
}
