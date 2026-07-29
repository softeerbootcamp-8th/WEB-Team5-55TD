package com.ootd.pickup.auction.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

  @Mock private ConsignmentRepository consignmentRepository;

  @Mock private AuctionRepository auctionRepository;

  private AuctionService auctionService;

  @BeforeEach
  void setUp() {
    auctionService = new AuctionService(consignmentRepository, auctionRepository);
  }

  @Test
  void 유효한_요청으로_경매를_신청하면_경매가_생성된다() {
    // given
    Long memberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, memberId, ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));
    given(auctionRepository.save(any(Auction.class)))
        .willAnswer(
            invocation -> {
              Auction auction = invocation.getArgument(0);
              ReflectionTestUtils.setField(auction, "auctionId", 1L);
              return auction;
            });

    LocalDateTime scheduledStartAt = LocalDateTime.now().plusDays(1);
    CreateAuctionRequest request =
        new CreateAuctionRequest(consignmentId, 10000L, 15000L, scheduledStartAt);

    // when
    CreateAuctionResponse response = auctionService.registerAuction(memberId, request);

    // then
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.consignmentId()).isEqualTo(consignmentId);
    assertThat(response.auctionStatus()).isEqualTo(AuctionStatus.SCHEDULED);
    assertThat(response.startingPrice()).isEqualTo(10000L);
    assertThat(response.bidIncrement()).isEqualTo(500L);
    assertThat(response.startedAt()).isEqualTo(scheduledStartAt);
    assertThat(response.endedAt()).isNull();
    assertThat(response.winningBidId()).isNull();
    assertThat(response.winningPrice()).isNull();
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.AUCTION_SCHEDULED);
  }

  @Test
  void 존재하지_않는_위탁상품이면_예외가_발생한다() {
    // given
    Long memberId = 1L;
    Long notExistConsignmentId = 999L;
    given(consignmentRepository.findConsignmentById(notExistConsignmentId))
        .willReturn(Optional.empty());

    CreateAuctionRequest request =
        new CreateAuctionRequest(
            notExistConsignmentId, 10000L, 15000L, LocalDateTime.now().plusDays(1));

    // when & then
    assertThatThrownBy(() -> auctionService.registerAuction(memberId, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 판매자_본인이_아니면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long requesterMemberId = 2L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, sellerMemberId, ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));

    CreateAuctionRequest request =
        new CreateAuctionRequest(consignmentId, 10000L, 15000L, LocalDateTime.now().plusDays(1));

    // when & then
    assertThatThrownBy(() -> auctionService.registerAuction(requesterMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 이미_경매_신청_가능한_상태가_아니면_예외가_발생한다() {
    // given
    Long memberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, memberId, ConsignmentStatus.AUCTION_SCHEDULED);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));

    CreateAuctionRequest request =
        new CreateAuctionRequest(consignmentId, 10000L, 15000L, LocalDateTime.now().plusDays(1));

    // when & then
    assertThatThrownBy(() -> auctionService.registerAuction(memberId, request))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            e ->
                assertThat(((PickUpException) e).getMessage())
                    .isEqualTo(ExceptionCode.CONSIGNMENT_NOT_REGISTERABLE.getMessage()));
    then(auctionRepository).shouldHaveNoInteractions();
  }

  private Consignment createConsignment(
      Long consignmentId, Long sellerMemberId, ConsignmentStatus status) {
    Consignment consignment =
        Consignment.builder().card(null).sellerMemberId(sellerMemberId).status(status).build();
    ReflectionTestUtils.setField(consignment, "consignmentId", consignmentId);
    return consignment;
  }
}
