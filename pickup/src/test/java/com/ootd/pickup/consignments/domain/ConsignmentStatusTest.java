package com.ootd.pickup.consignments.domain;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class ConsignmentStatusTest {

  @Test
  void 유효한_상태_문자열이면_해당_상태를_반환한다() {
    // when & then
    assertThat(ConsignmentStatus.from("registerable")).isEqualTo(ConsignmentStatus.REGISTERABLE);
  }

  @Test
  void null이면_null을_반환한다() {
    // when & then
    assertThat(ConsignmentStatus.from(null)).isNull();
  }

  @Test
  void 유효하지_않은_상태_문자열이면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> ConsignmentStatus.from("존재하지않는상태"))
        .isInstanceOf(PickUpException.class);
  }

  @Test
  void 등록가능_상태면_수정_가능하다() {
    // when & then
    assertThat(ConsignmentStatus.REGISTERABLE.isModifiable()).isTrue();
  }

  @Test
  void 유찰_상태면_수정_가능하다() {
    // when & then
    assertThat(ConsignmentStatus.PASSED.isModifiable()).isTrue();
  }

  @Test
  void 경매등록_상태면_수정_불가능하다() {
    // when & then
    assertThat(ConsignmentStatus.AUCTION_SCHEDULED.isModifiable()).isFalse();
  }

  @Test
  void 경매진행_상태면_수정_불가능하다() {
    // when & then
    assertThat(ConsignmentStatus.AUCTION_ONGOING.isModifiable()).isFalse();
  }

  @Test
  void 낙찰_상태면_수정_불가능하다() {
    // when & then
    assertThat(ConsignmentStatus.WON.isModifiable()).isFalse();
  }

  @Test
  void 등록가능_상태면_삭제_가능하다() {
    // when & then
    assertThat(ConsignmentStatus.REGISTERABLE.isDeletable()).isTrue();
  }

  @Test
  void 유찰_상태면_삭제_가능하다() {
    // when & then
    assertThat(ConsignmentStatus.PASSED.isDeletable()).isTrue();
  }

  @Test
  void 경매등록_상태면_삭제_불가능하다() {
    // when & then
    assertThat(ConsignmentStatus.AUCTION_SCHEDULED.isDeletable()).isFalse();
  }

  @Test
  void 경매진행_상태면_삭제_불가능하다() {
    // when & then
    assertThat(ConsignmentStatus.AUCTION_ONGOING.isDeletable()).isFalse();
  }

  @Test
  void 낙찰_상태면_삭제_불가능하다() {
    // when & then
    assertThat(ConsignmentStatus.WON.isDeletable()).isFalse();
  }
}
