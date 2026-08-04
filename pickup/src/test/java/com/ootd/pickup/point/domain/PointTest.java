package com.ootd.pickup.point.domain;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class PointTest {

  @Test
  void 포인트를_생성하면_회원ID와_0잔액으로_초기화된다() {
    // given & when
    Point point = Point.create(1L);

    // then
    assertThat(point.getMemberId()).isEqualTo(1L);
    assertThat(point.getBalance()).isZero();
  }

  @Test
  void 양수만큼_잔액을_조정하면_잔액이_증가한다() {
    // given
    Point point = Point.create(1L);

    // when
    point.adjustBalance(1000L);

    // then
    assertThat(point.getBalance()).isEqualTo(1000L);
  }

  @Test
  void 음수만큼_잔액을_조정하면_잔액이_감소한다() {
    // given
    Point point = Point.create(1L);
    point.adjustBalance(1000L);

    // when
    point.adjustBalance(-400L);

    // then
    assertThat(point.getBalance()).isEqualTo(600L);
  }

  @Test
  void 조정_결과_잔액이_0보다_작아지면_예외가_발생한다() {
    // given
    Point point = Point.create(1L);
    point.adjustBalance(500L);

    // when & then
    assertThatThrownBy(() -> point.adjustBalance(-600L)).isInstanceOf(PickUpException.class);
    assertThat(point.getBalance()).isEqualTo(500L);
  }
}
