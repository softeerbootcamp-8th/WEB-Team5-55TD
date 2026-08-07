package com.ootd.pickup.point.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void 양수_금액으로_증가시키면_잔액이_늘어난다() {
    // given
    Point point = Point.create(1L);

    // when
    point.increaseBalance(1_000L);

    // then
    assertThat(point.getBalance()).isEqualTo(1_000L);
  }

  @Test
  void 양수_금액으로_감소시키면_잔액이_줄어든다() {
    // given
    Point point = Point.create(1L);
    point.increaseBalance(1_000L);

    // when
    point.decreaseBalance(300L);

    // then
    assertThat(point.getBalance()).isEqualTo(700L);
  }

  @Test
  void 양수가_아닌_금액으로_증가시키면_예외가_발생한다() {
    // given
    Point point = Point.create(1L);

    // when & then
    assertThatThrownBy(() -> point.increaseBalance(0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> point.increaseBalance(-1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 양수가_아닌_금액으로_감소시키면_예외가_발생한다() {
    // given
    Point point = Point.create(1L);

    // when & then
    assertThatThrownBy(() -> point.decreaseBalance(0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> point.decreaseBalance(-1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
