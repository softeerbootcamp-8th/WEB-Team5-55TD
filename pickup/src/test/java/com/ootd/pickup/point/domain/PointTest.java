package com.ootd.pickup.point.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void 포인트를_예약하고_해제하면_사용가능잔액이_변한다() {
    // given
    Point point = Point.create(1L);
    point.increaseBalance(1_000L);

    // when
    point.reserve(700L);

    // then
    assertThat(point.getBalance()).isEqualTo(1_000L);
    assertThat(point.getReservedBalance()).isEqualTo(700L);
    assertThat(point.getAvailableBalance()).isEqualTo(300L);

    // when
    point.release(200L);

    // then
    assertThat(point.getReservedBalance()).isEqualTo(500L);
    assertThat(point.getAvailableBalance()).isEqualTo(500L);
  }

  @Test
  void 예약포인트를_포착하면_총액과_예약액이_함께_감소한다() {
    // given
    Point point = Point.create(1L);
    point.increaseBalance(1_000L);
    point.reserve(700L);

    // when
    point.capture(700L);

    // then
    assertThat(point.getBalance()).isEqualTo(300L);
    assertThat(point.getReservedBalance()).isZero();
    assertThat(point.getAvailableBalance()).isEqualTo(300L);
  }

  @Test
  void 사용가능잔액보다_큰_예약과_차감을_거부한다() {
    // given
    Point point = Point.create(1L);
    point.increaseBalance(1_000L);
    point.reserve(700L);

    // when & then
    assertThatThrownBy(() -> point.reserve(301L)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> point.decreaseBalance(301L)).isInstanceOf(IllegalStateException.class);
  }
}
