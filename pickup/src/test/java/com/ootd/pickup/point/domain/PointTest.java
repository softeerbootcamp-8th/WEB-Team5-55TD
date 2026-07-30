package com.ootd.pickup.point.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
}
