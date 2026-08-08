package com.ootd.pickup.point.service;

import static com.ootd.pickup.global.exception.ExceptionCode.INSUFFICIENT_BID_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointReservation;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.point.repository.PointReservationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PointReservationServiceTest {

  @Mock private PointRepository pointRepository;
  @Mock private PointReservationRepository pointReservationRepository;

  private PointReservationService pointReservationService;

  @BeforeEach
  void setUp() {
    pointReservationService =
        new PointReservationService(pointRepository, pointReservationRepository);
  }

  @Test
  void 새_최고입찰금액을_예약한다() {
    // given
    Auction auction = createAuction(1L);
    Member bidder = createMember(2L);
    Bid bid = createBid(auction, bidder, 10_500L, 10L);
    Point point = createPoint(2L, 20_000L);
    given(pointReservationRepository.findByAuctionIdForUpdate(1L)).willReturn(Optional.empty());
    given(pointRepository.findByMemberIdForUpdate(2L)).willReturn(Optional.of(point));

    // when
    pointReservationService.reserveHighestBid(auction, bid, bidder);

    // then
    assertThat(point.getReservedBalance()).isEqualTo(10_500L);
    assertThat(point.getAvailableBalance()).isEqualTo(9_500L);
  }

  @Test
  void 추월되면_기존예약을_해제하고_새입찰자에게_예약한다() {
    // given
    Auction auction = createAuction(1L);
    Member previousBidder = createMember(2L);
    Member newBidder = createMember(3L);
    Bid previousBid = createBid(auction, previousBidder, 10_500L, 10L);
    Bid newBid = createBid(auction, newBidder, 11_000L, 11L);
    Point previousPoint = createPoint(2L, 20_000L);
    previousPoint.reserve(10_500L);
    Point newPoint = createPoint(3L, 20_000L);
    PointReservation reservation =
        PointReservation.create(auction, previousBid, previousBidder, 10_500L);
    given(pointReservationRepository.findByAuctionIdForUpdate(1L))
        .willReturn(Optional.of(reservation));
    given(pointRepository.findByMemberIdForUpdate(2L)).willReturn(Optional.of(previousPoint));
    given(pointRepository.findByMemberIdForUpdate(3L)).willReturn(Optional.of(newPoint));

    // when
    pointReservationService.reserveHighestBid(auction, newBid, newBidder);

    // then
    assertThat(previousPoint.getReservedBalance()).isZero();
    assertThat(newPoint.getReservedBalance()).isEqualTo(11_000L);
    assertThat(reservation.getMember().getMemberId()).isEqualTo(3L);
    assertThat(reservation.getAmount()).isEqualTo(11_000L);
  }

  @Test
  void 여러경매에_예약된_금액을_제외한_가용액보다_큰_입찰을_거부한다() {
    // given
    Auction auction = createAuction(1L);
    Member bidder = createMember(2L);
    Bid bid = createBid(auction, bidder, 11_000L, 10L);
    Point point = createPoint(2L, 20_000L);
    point.reserve(10_000L);
    given(pointReservationRepository.findByAuctionIdForUpdate(1L)).willReturn(Optional.empty());
    given(pointRepository.findByMemberIdForUpdate(2L)).willReturn(Optional.of(point));

    // when & then
    assertThatThrownBy(() -> pointReservationService.reserveHighestBid(auction, bid, bidder))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(INSUFFICIENT_BID_LIMIT.getClientExceptionCode().name()));
    assertThat(point.getReservedBalance()).isEqualTo(10_000L);
  }

  @Test
  void 유찰되면_활성예약을_즉시_해제한다() {
    // given
    Auction auction = createAuction(1L);
    Member bidder = createMember(2L);
    Bid bid = createBid(auction, bidder, 10_500L, 10L);
    Point point = createPoint(2L, 20_000L);
    point.reserve(10_500L);
    PointReservation reservation = PointReservation.create(auction, bid, bidder, 10_500L);
    given(pointReservationRepository.findByAuctionIdForUpdate(1L))
        .willReturn(Optional.of(reservation));
    given(pointRepository.findByMemberIdForUpdate(2L)).willReturn(Optional.of(point));

    // when
    pointReservationService.releaseForPassedAuction(1L);

    // then
    assertThat(point.getReservedBalance()).isZero();
    assertThat(point.getAvailableBalance()).isEqualTo(20_000L);
    assertThat(reservation.getReservationStatus().name()).isEqualTo("RELEASED");
  }

  private Auction createAuction(Long auctionId) {
    Consignment consignment =
        Consignment.builder().status(ConsignmentStatus.AUCTION_SCHEDULED).build();
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("login" + memberId, "password", "nickname" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Bid createBid(Auction auction, Member member, long amount, Long bidId) {
    Bid bid = Bid.create(auction, member, amount);
    ReflectionTestUtils.setField(bid, "bidId", bidId);
    return bid;
  }

  private Point createPoint(Long memberId, long balance) {
    Point point = Point.create(memberId);
    point.increaseBalance(balance);
    return point;
  }
}
