package com.ootd.pickup.consignments.domain;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class ConsignmentTest {

  @Test
  void 경매예정_상품의_경매가_취소되면_유찰_상태가_된다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.AUCTION_SCHEDULED);

    // when
    consignment.markAuctionCancelled();

    // then
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.PASSED);
  }

  @Test
  void 경매진행중_상품의_경매가_취소되면_유찰_상태가_된다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.AUCTION_ONGOING);

    // when
    consignment.markAuctionCancelled();

    // then
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.PASSED);
  }

  @Test
  void 경매등록_가능_상품의_경매취소를_시도하면_예외가_발생한다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.REGISTERABLE);

    // when & then
    assertThatThrownBy(consignment::markAuctionCancelled).isInstanceOf(PickUpException.class);
  }

  @Test
  void 등록가능_상태의_상품을_차단하면_차단_상태가_된다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.REGISTERABLE);

    // when
    consignment.block();

    // then
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.BLOCKED);
  }

  @Test
  void 유찰_상태의_상품을_차단하면_차단_상태가_된다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.PASSED);

    // when
    consignment.block();

    // then
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.BLOCKED);
  }

  @Test
  void 경매진행중인_상품을_차단하려하면_예외가_발생한다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.AUCTION_ONGOING);

    // when & then
    assertThatThrownBy(consignment::block).isInstanceOf(PickUpException.class);
  }

  @Test
  void 차단된_상품을_차단해제하면_등록가능_상태가_된다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.BLOCKED);

    // when
    consignment.unblock();

    // then
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.REGISTERABLE);
  }

  @Test
  void 차단되지_않은_상품을_차단해제하려하면_예외가_발생한다() {
    // given
    Consignment consignment = createConsignment(ConsignmentStatus.REGISTERABLE);

    // when & then
    assertThatThrownBy(consignment::unblock).isInstanceOf(PickUpException.class);
  }

  private Consignment createConsignment(ConsignmentStatus status) {
    return Consignment.builder().card(null).sellerMember(null).status(status).build();
  }
}
