package com.ootd.pickup.auction.repository.auction;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class AuctionCursorTest {

  @Test
  void 인코딩한_커서를_디코딩하면_원래_값을_반환한다() {
    // given
    String encoded = AuctionCursor.encode(AuctionSort.PRICE_ASC, 10000L, 5L);

    // when
    AuctionCursor cursor = AuctionCursor.decode(encoded, AuctionSort.PRICE_ASC);

    // then
    assertThat(cursor.sort()).isEqualTo(AuctionSort.PRICE_ASC);
    assertThat(cursor.sortValue()).isEqualTo(10000L);
    assertThat(cursor.auctionId()).isEqualTo(5L);
  }

  @Test
  void 커서가_없으면_null을_반환한다() {
    assertThat(AuctionCursor.decode(null, AuctionSort.RECENT)).isNull();
    assertThat(AuctionCursor.decode("  ", AuctionSort.RECENT)).isNull();
  }

  @Test
  void 잘못된_형식의_커서면_예외가_발생한다() {
    assertThatThrownBy(() -> AuctionCursor.decode("not-a-valid-base64!!", AuctionSort.RECENT))
        .isInstanceOf(PickUpException.class);
  }

  @Test
  void 커서의_정렬기준이_현재_요청과_다르면_예외가_발생한다() {
    // given
    String encoded = AuctionCursor.encode(AuctionSort.PRICE_ASC, 10000L, 5L);

    // when & then
    assertThatThrownBy(() -> AuctionCursor.decode(encoded, AuctionSort.RECENT))
        .isInstanceOf(PickUpException.class);
  }
}
