package com.ootd.pickup.consignments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class ConsignmentTest {

  @Test
  void 등록_가능_상품은_경매_신청하면_예정_상태가_된다() {
    // given
    Consignment consignment = consignmentOf(ConsignmentStatus.REGISTERABLE);

    // when
    consignment.scheduleAuction();

    // then
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.IN_AUCTION);
  }

  @Test
  void 이미_경매가_진행중인_상품은_다시_신청할_수_없다() {
    // given
    Consignment consignment = consignmentOf(ConsignmentStatus.IN_AUCTION);

    // when & then
    assertThatThrownBy(consignment::scheduleAuction).isInstanceOf(PickUpException.class);
  }

  @Test
  void 판매완료된_상품은_다시_신청할_수_없다() {
    // given
    Consignment consignment = consignmentOf(ConsignmentStatus.SOLD);

    // when & then
    assertThatThrownBy(consignment::scheduleAuction).isInstanceOf(PickUpException.class);
  }

  private Consignment consignmentOf(ConsignmentStatus status) {
    return Consignment.builder().card(null).sellerMember(null).status(status).build();
  }
}
