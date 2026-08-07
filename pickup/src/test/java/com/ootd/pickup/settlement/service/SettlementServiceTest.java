package com.ootd.pickup.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.service.AuctionManageService;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.settlement.domain.Settlement;
import com.ootd.pickup.settlement.domain.SettlementType;
import com.ootd.pickup.settlement.repository.SettlementRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

  @Mock private AuctionManageService auctionManageService;
  @Mock private MemberManageService memberManageService;
  @Mock private PointRepository pointRepository;
  @Mock private SettlementRepository settlementRepository;

  @Captor private ArgumentCaptor<Settlement> settlementCaptor;
  @Captor private ArgumentCaptor<Point> pointCaptor;

  private SettlementService settlementService;

  @BeforeEach
  void setUp() {
    settlementService =
        new SettlementService(
            auctionManageService, memberManageService, pointRepository, settlementRepository);
  }

  @Test
  void 낙찰된_경매를_정산하면_낙찰자와_판매자의_정산과_포인트가_갱신된다() {
    // given
    Auction auction = createAuction(1L);
    Member winner = createMember(2L);
    Member seller = createMember(3L);
    Point winnerPoint = createPoint(2L, 20_000L);
    Point sellerPoint = createPoint(3L, 5_000L);

    given(
            settlementRepository.existsByAuctionIdAndMemberIdAndSettlementType(
                1L, 2L, SettlementType.WINNER_PAYMENT))
        .willReturn(false);
    given(
            settlementRepository.existsByAuctionIdAndMemberIdAndSettlementType(
                1L, 3L, SettlementType.SELLER_PAYOUT))
        .willReturn(false);
    given(auctionManageService.getAuctionById(1L)).willReturn(auction);
    given(memberManageService.getMemberById(2L)).willReturn(winner);
    given(memberManageService.getMemberById(3L)).willReturn(seller);
    given(pointRepository.findByMemberIdForUpdate(2L)).willReturn(Optional.of(winnerPoint));
    given(pointRepository.findByMemberIdForUpdate(3L)).willReturn(Optional.of(sellerPoint));

    // when
    settlementService.settleAuction(1L, 2L, 3L, 10_500L);

    // then
    then(settlementRepository).should(times(2)).save(settlementCaptor.capture());
    then(pointRepository).should(times(2)).save(pointCaptor.capture());
    assertThat(settlementCaptor.getAllValues())
        .extracting(Settlement::getSettlementType)
        .containsExactlyInAnyOrder(SettlementType.WINNER_PAYMENT, SettlementType.SELLER_PAYOUT);
    assertThat(winnerPoint.getBalance()).isEqualTo(9_500L);
    assertThat(sellerPoint.getBalance()).isEqualTo(15_500L);
  }

  @Test
  void 판매자_id가_낙찰자_id보다_작아도_memberId_오름차순으로_포인트를_잠근다() {
    // given: winnerMemberId(5L) > sellerMemberId(2L) — 낙찰자/판매자 순서와 memberId 순서가 반대인 경우
    Auction auction = createAuction(1L);
    Member winner = createMember(5L);
    Member seller = createMember(2L);
    Point winnerPoint = createPoint(5L, 20_000L);
    Point sellerPoint = createPoint(2L, 5_000L);

    given(auctionManageService.getAuctionById(1L)).willReturn(auction);
    given(memberManageService.getMemberById(5L)).willReturn(winner);
    given(memberManageService.getMemberById(2L)).willReturn(seller);
    given(pointRepository.findByMemberIdForUpdate(5L)).willReturn(Optional.of(winnerPoint));
    given(pointRepository.findByMemberIdForUpdate(2L)).willReturn(Optional.of(sellerPoint));

    // when
    settlementService.settleAuction(1L, 5L, 2L, 10_500L);

    // then: memberId가 더 작은 판매자(2L)의 포인트 락을 먼저 획득해야 한다
    InOrder inOrder = inOrder(pointRepository);
    inOrder.verify(pointRepository).findByMemberIdForUpdate(2L);
    inOrder.verify(pointRepository).findByMemberIdForUpdate(5L);
  }

  @Test
  void 유찰된_경매를_정산하면_아무것도_하지_않는다() {
    // when
    settlementService.settleAuction(1L, null, 3L, null);

    // then
    then(settlementRepository).should(never()).save(any(Settlement.class));
    then(pointRepository).should(never()).save(any(Point.class));
  }

  @Test
  void 이미_처리된_정산이면_다시_처리하지_않는다() {
    // given
    given(
            settlementRepository.existsByAuctionIdAndMemberIdAndSettlementType(
                1L, 2L, SettlementType.WINNER_PAYMENT))
        .willReturn(true);
    given(
            settlementRepository.existsByAuctionIdAndMemberIdAndSettlementType(
                1L, 3L, SettlementType.SELLER_PAYOUT))
        .willReturn(true);

    // when
    settlementService.settleAuction(1L, 2L, 3L, 10_500L);

    // then
    then(settlementRepository).should(never()).save(any(Settlement.class));
    then(pointRepository).should(never()).save(any(Point.class));
    then(auctionManageService).should(never()).getAuctionById(any());
  }

  private Auction createAuction(Long auctionId) {
    Consignment consignment =
        Consignment.builder().status(ConsignmentStatus.AUCTION_SCHEDULED).build();
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now())
            .auctionStatus(AuctionStatus.WON)
            .startingPrice(10_000L)
            .reservePrice(10_000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("loginId" + memberId, "password", "닉네임" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Point createPoint(Long memberId, long balance) {
    Point point = Point.create(memberId);
    ReflectionTestUtils.setField(point, "balance", balance);
    return point;
  }
}
