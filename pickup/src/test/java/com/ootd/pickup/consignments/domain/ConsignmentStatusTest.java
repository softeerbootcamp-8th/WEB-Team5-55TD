package com.ootd.pickup.consignments.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConsignmentStatusTest {

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
