package com.ootd.pickup.point.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ootd.pickup.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PointTransactionTest {

  @Test
  void 충전_거래를_생성하면_금액이_양수로_기록되고_경매와_연결되지_않는다() {
    // given
    Member member = createMember(1L);

    // when
    PointTransaction transaction = PointTransaction.forCharge(member, 300_000L, 500_000L, "req-1");

    // then
    assertThat(transaction.getTransactionType()).isEqualTo(PointTransactionType.CHARGE);
    assertThat(transaction.getAmount()).isEqualTo(300_000L);
    assertThat(transaction.getBalanceAfter()).isEqualTo(500_000L);
    assertThat(transaction.getAuction()).isNull();
  }

  @Test
  void 충전_거래의_멱등키는_CHARGE_접두사와_요청ID로_구성된다() {
    // given
    Member member = createMember(1L);

    // when
    PointTransaction transaction = PointTransaction.forCharge(member, 300_000L, 500_000L, "req-1");

    // then
    assertThat(transaction.getIdempotencyKey()).isEqualTo("CHARGE:req-1");
    assertThat(PointTransaction.chargeIdempotencyKey("req-1")).isEqualTo("CHARGE:req-1");
  }

  @Test
  void 충전_금액이_0이면_예외가_발생한다() {
    // given
    Member member = createMember(1L);

    // when & then
    assertThatThrownBy(() -> PointTransaction.forCharge(member, 0L, 500_000L, "req-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("login" + memberId, "password", "nickname" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }
}
