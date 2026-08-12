package com.ootd.pickup.point.service;

import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_CHARGE_AMOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.any;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointTransaction;
import com.ootd.pickup.point.dto.response.PointChargeResponse;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.point.repository.PointTransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PointChargeServiceTest {

  @Mock private MemberManageService memberManageService;
  @Mock private PointLockService pointLockService;
  @Mock private PointRepository pointRepository;
  @Mock private PointTransactionRepository pointTransactionRepository;

  private PointChargeService pointChargeService;

  @BeforeEach
  void setUp() {
    pointChargeService =
        new PointChargeService(
            memberManageService, pointLockService, pointRepository, pointTransactionRepository);
  }

  @Test
  void 유효한_금액으로_충전하면_잔액이_증가하고_거래내역이_저장된다() {
    // given
    Member member = createMember(1L);
    Point point = createPoint(1L, 200_000L);
    given(pointTransactionRepository.findByIdempotencyKey("CHARGE:req-1"))
        .willReturn(Optional.empty());
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(pointLockService.getPointForUpdate(1L)).willReturn(point);

    // when
    PointChargeResponse response = pointChargeService.chargePoint(1L, 300_000L, "req-1");

    // then
    assertThat(point.getBalance()).isEqualTo(500_000L);
    assertThat(response.chargedAmount()).isEqualTo(300_000L);
    assertThat(response.pointBalance()).isEqualTo(500_000L);
    then(pointRepository).should().save(point);
    then(pointTransactionRepository).should().save(any(PointTransaction.class));
  }

  @Test
  void 최소금액보다_작으면_예외가_발생하고_잠금을_시도하지_않는다() {
    // when & then
    assertThatThrownBy(() -> pointChargeService.chargePoint(1L, 99_999L, "req-1"))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(INVALID_CHARGE_AMOUNT.getClientExceptionCode().name()));
    then(pointLockService).shouldHaveNoInteractions();
    then(pointTransactionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 최대금액보다_크면_예외가_발생하고_잠금을_시도하지_않는다() {
    // when & then
    assertThatThrownBy(() -> pointChargeService.chargePoint(1L, 10_000_001L, "req-1"))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(INVALID_CHARGE_AMOUNT.getClientExceptionCode().name()));
    then(pointLockService).shouldHaveNoInteractions();
  }

  @Test
  void 이미_처리된_idempotencyKey면_잠금_없이_기존_결과를_반환한다() {
    // given
    Member member = createMember(1L);
    Point point = createPoint(1L, 500_000L);
    PointTransaction existing = PointTransaction.forCharge(member, 300_000L, 500_000L, "req-1");
    given(pointTransactionRepository.findByIdempotencyKey("CHARGE:req-1"))
        .willReturn(Optional.of(existing));
    given(pointRepository.findByMemberId(1L)).willReturn(Optional.of(point));

    // when
    PointChargeResponse response = pointChargeService.chargePoint(1L, 300_000L, "req-1");

    // then
    assertThat(response.chargedAmount()).isEqualTo(300_000L);
    assertThat(response.pointBalance()).isEqualTo(500_000L);
    then(pointLockService).shouldHaveNoInteractions();
    then(pointRepository).should().findByMemberId(1L);
  }

  @Test
  void getChargeResult로_기존_충전결과를_다시_조회할_수_있다() {
    // given
    Member member = createMember(1L);
    Point point = createPoint(1L, 500_000L);
    PointTransaction existing = PointTransaction.forCharge(member, 300_000L, 500_000L, "req-1");
    given(pointTransactionRepository.findByIdempotencyKey("CHARGE:req-1"))
        .willReturn(Optional.of(existing));
    given(pointRepository.findByMemberId(1L)).willReturn(Optional.of(point));

    // when
    PointChargeResponse response = pointChargeService.getChargeResult(1L, "req-1");

    // then
    assertThat(response.chargedAmount()).isEqualTo(300_000L);
    assertThat(response.pointBalance()).isEqualTo(500_000L);
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("login" + memberId, "password", "nickname" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Point createPoint(Long memberId, long balance) {
    Point point = Point.create(memberId);
    point.increaseBalance(balance);
    return point;
  }
}
