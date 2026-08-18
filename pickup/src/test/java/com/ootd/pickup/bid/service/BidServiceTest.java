package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_ENDED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_STARTED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.ENDED_AUCTION_BIDS_SELLER_ONLY;
import static com.ootd.pickup.global.exception.ExceptionCode.ILLEGAL_ARGUMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.INSUFFICIENT_BID_LIMIT;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_CURSOR;
import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.BidRequestSucceededNotificationEvent;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.dto.request.GetAuctionBidsRequest;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.AuctionBidListItemResponse;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.service.PointReservationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

  @Mock private AuctionRepository auctionRepository;

  @Mock private BidRepository bidRepository;

  @Mock private MemberRepository memberRepository;
  @Mock private PointReservationService pointReservationService;
  @Mock private EventPublisher eventPublisher;
  @Mock private ImageUrlResolver imageUrlResolver;

  private BidService bidService;

  @BeforeEach
  void setUp() {
    bidService =
        new BidService(
            auctionRepository,
            bidRepository,
            memberRepository,
            pointReservationService,
            eventPublisher,
            imageUrlResolver);
  }

  @Test
  void 첫_입찰가가_시작가와_최소단위의_합이면_최고입찰로_저장된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    ReflectionTestUtils.setField(bidder, "profileImageObjectKey", "profiles/2.webp");
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(imageUrlResolver.resolve("profiles/2.webp"))
        .willReturn("https://images.test/profiles/2.webp");
    given(bidRepository.save(any(Bid.class)))
        .willAnswer(
            invocation -> {
              Bid bid = invocation.getArgument(0);
              ReflectionTestUtils.setField(bid, "bidId", 10L);
              return bid;
            });

    // when
    PlaceBidResponse response = bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L));

    // then
    assertThat(response.bidId()).isEqualTo(10L);
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.memberId()).isEqualTo(2L);
    assertThat(response.bidPrice()).isEqualTo(10_500L);
    assertThat(response.bidStatus()).isEqualTo(BidStatus.HIGHEST);
    assertThat(auction.getWinningBidId()).isEqualTo(10L);
    assertThat(auction.getWinningPrice()).isEqualTo(10_500L);
    then(auctionRepository).should().save(auction);
    ArgumentCaptor<NotificationEvent> eventCaptor =
        ArgumentCaptor.forClass(NotificationEvent.class);
    then(eventPublisher).should().publish(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isInstanceOfSatisfying(
            BidRequestSucceededNotificationEvent.class,
            event -> {
              assertThat(event.auctionId()).isEqualTo(1L);
              assertThat(event.winningBid().bidId()).isEqualTo(10L);
              assertThat(event.winningBid().profileImageUrl())
                  .isEqualTo("https://images.test/profiles/2.webp");
              assertThat(event.winningPrice()).isEqualTo(10_500L);
              assertThat(event.bidRequestId()).isNull();
            });
  }

  @Test
  void 더_높은_입찰이_성공하면_기존_최고입찰은_추월상태가_된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member previousBidder = createMember(2L);
    Member newBidder = createMember(3L);
    Bid previousHighestBid = Bid.create(auction, previousBidder, 10_500L);
    ReflectionTestUtils.setField(previousHighestBid, "bidId", 10L);
    auction.updateWinningBid(previousHighestBid.getBidId(), previousHighestBid.getBidPrice());
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(3L)).willReturn(Optional.of(newBidder));
    given(bidRepository.save(any(Bid.class)))
        .willAnswer(
            invocation -> {
              Bid bid = invocation.getArgument(0);
              if (bid.getBidId() == null) {
                ReflectionTestUtils.setField(bid, "bidId", 11L);
              }
              return bid;
            });

    // when
    PlaceBidResponse response = bidService.placeBid(1L, 3L, new PlaceBidRequest(11_000L));

    // then
    assertThat(previousHighestBid.getBidStatus()).isEqualTo(BidStatus.OUTBID);
    assertThat(response.bidId()).isEqualTo(11L);
    assertThat(response.bidStatus()).isEqualTo(BidStatus.HIGHEST);
    assertThat(auction.getWinningBidId()).isEqualTo(11L);
    assertThat(auction.getWinningPrice()).isEqualTo(11_000L);
    then(auctionRepository).should().save(auction);
  }

  @Test
  void 현재가_이하로_입찰하면_더_높은_입찰_존재_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    Bid previousHighestBid = Bid.create(auction, createMember(3L), 10_500L);
    auction.updateWinningBid(previousHighestBid.getBidId(), previousHighestBid.getBidPrice());
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), OUTBID_EXISTS);
    then(bidRepository).should(never()).save(any(Bid.class));
  }

  @Test
  void 현재가보다_높지만_최소단위_미만이면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    Bid previousHighestBid = Bid.create(auction, createMember(3L), 10_500L);
    auction.updateWinningBid(previousHighestBid.getBidId(), previousHighestBid.getBidPrice());
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_999L)), BELOW_MIN_INCREMENT);
  }

  @Test
  void 보유_포인트보다_높은_금액으로_입찰하면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    willThrow(new PickUpException(INSUFFICIENT_BID_LIMIT))
        .given(pointReservationService)
        .prepareReservation(eq(auction), eq(bidder), eq(10_500L));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), INSUFFICIENT_BID_LIMIT);
    then(bidRepository).should(never()).save(any(Bid.class));
  }

  @Test
  void 보유_포인트와_같은_금액으로_입찰하면_성공한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(bidRepository.save(any(Bid.class)))
        .willAnswer(
            invocation -> {
              Bid bid = invocation.getArgument(0);
              ReflectionTestUtils.setField(bid, "bidId", 10L);
              return bid;
            });

    // when
    PlaceBidResponse response = bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L));

    // then
    assertThat(response.bidPrice()).isEqualTo(10_500L);
  }

  @Test
  void 시작되지_않은_경매에_입찰하면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.SCHEDULED, LocalDateTime.now().plusHours(1));
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), AUCTION_NOT_STARTED);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 종료된_상태의_경매에_입찰하면_예외가_발생한다() {
    // given
    Auction auction = createAuction(1L, 1L, AuctionStatus.WON, LocalDateTime.now().minusMinutes(1));
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), AUCTION_ENDED);
  }

  @Test
  void 진행상태여도_종료시간이_지났으면_입찰할_수_없다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().minusMinutes(1));
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), AUCTION_ENDED);
  }

  @Test
  void 판매자가_본인의_경매에_입찰하면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 1L, new PlaceBidRequest(10_500L)),
        AUCTION_SELLER_BID_FORBIDDEN);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 존재하지_않는_경매에_입찰하면_예외가_발생한다() {
    // given
    given(auctionRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(999L, 2L, new PlaceBidRequest(10_500L)), AUCTION_NOT_FOUND);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 경매_입찰_내역을_조회하면_최근_입찰_순으로_닉네임과_함께_반환된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member viewer = createMember(2L);
    ReflectionTestUtils.setField(viewer, "profileImageObjectKey", "profiles/2.webp");
    Member other = createMember(3L);
    Bid myBid = createBid(auction, viewer, 11_000L, 101L);
    Bid otherBid = createBid(auction, other, 10_500L, 100L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(bidRepository.findAllByAuctionId(1L, null, 21)).willReturn(List.of(myBid, otherBid));
    given(imageUrlResolver.resolve("profiles/2.webp"))
        .willReturn("https://images.test/profiles/2.webp");

    // when
    CursorPageResponse<AuctionBidListItemResponse, String> response =
        bidService.getAuctionBids(1L, 2L, new GetAuctionBidsRequest(null, 20));

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.cursor()).isNull();
    assertThat(response.items()).hasSize(2);

    AuctionBidListItemResponse first = response.items().get(0);
    assertThat(first.bidId()).isEqualTo(101L);
    assertThat(first.nickname()).isEqualTo("닉네임2");
    assertThat(first.profileImageUrl()).isEqualTo("https://images.test/profiles/2.webp");
    assertThat(first.bidPrice()).isEqualTo(11_000L);
    assertThat(first.isMine()).isTrue();

    AuctionBidListItemResponse second = response.items().get(1);
    assertThat(second.isMine()).isFalse();
  }

  @Test
  void 비로그인_상태로_조회하면_isMine이_모두_false다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Bid bid = createBid(auction, createMember(3L), 10_500L, 100L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(bidRepository.findAllByAuctionId(1L, null, 21)).willReturn(List.of(bid));

    // when
    CursorPageResponse<AuctionBidListItemResponse, String> response =
        bidService.getAuctionBids(1L, null, new GetAuctionBidsRequest(null, 20));

    // then
    assertThat(response.items())
        .extracting(AuctionBidListItemResponse::isMine)
        .containsExactly(false);
  }

  @Test
  void 입찰_내역이_size보다_많으면_hasNext가_true이고_커서가_마지막_입찰ID다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Bid bidA = createBid(auction, createMember(2L), 11_000L, 101L);
    Bid bidB = createBid(auction, createMember(3L), 10_500L, 100L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(bidRepository.findAllByAuctionId(1L, null, 2)).willReturn(List.of(bidA, bidB));

    // when
    CursorPageResponse<AuctionBidListItemResponse, String> response =
        bidService.getAuctionBids(1L, null, new GetAuctionBidsRequest(null, 1));

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isEqualTo("101");
    assertThat(response.items())
        .extracting(AuctionBidListItemResponse::bidId)
        .containsExactly(101L);
  }

  @Test
  void 존재하지_않는_경매의_입찰_내역을_조회하면_예외가_발생한다() {
    // given
    given(auctionRepository.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertExceptionCode(
        () -> bidService.getAuctionBids(999L, null, new GetAuctionBidsRequest(null, 20)),
        AUCTION_NOT_FOUND);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 입찰_내역_조회시_유효하지_않은_커서값이면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.getAuctionBids(1L, null, new GetAuctionBidsRequest("not-a-number", 20)),
        INVALID_CURSOR);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 입찰_내역_조회시_size가_1보다_작으면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.getAuctionBids(1L, null, new GetAuctionBidsRequest(null, 0)),
        ILLEGAL_ARGUMENT);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 최고_입찰중인_경매가_있으면_true를_반환한다() {
    // given
    given(bidRepository.existsCurrentHighestBidByMemberId(2L)).willReturn(true);

    // when
    boolean hasActiveBid = bidService.hasActiveBid(2L);

    // then
    assertThat(hasActiveBid).isTrue();
  }

  @Test
  void 최고_입찰중인_경매가_없으면_false를_반환한다() {
    // given
    given(bidRepository.existsCurrentHighestBidByMemberId(2L)).willReturn(false);

    // when
    boolean hasActiveBid = bidService.hasActiveBid(2L);

    // then
    assertThat(hasActiveBid).isFalse();
  }

  private Bid createBid(Auction auction, Member member, Long bidPrice, Long bidId) {
    Bid bid = Bid.create(auction, member, bidPrice);
    ReflectionTestUtils.setField(bid, "bidId", bidId);
    return bid;
  }

  private void assertExceptionCode(Runnable runnable, ExceptionCode exceptionCode) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(exceptionCode.getClientExceptionCode().name()));
  }

  @Test
  void 종료된_경매의_입찰내역을_판매자가_조회하면_정상적으로_반환한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.WON, LocalDateTime.now().minusMinutes(10));
    Bid bid = createBid(auction, createMember(2L), 11_000L, 101L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(bidRepository.findAllByAuctionId(1L, null, 21)).willReturn(List.of(bid));

    // when
    CursorPageResponse<AuctionBidListItemResponse, String> response =
        bidService.getAuctionBids(1L, 1L, new GetAuctionBidsRequest(null, 20));

    // then
    assertThat(response.items()).hasSize(1);
  }

  @Test
  void 종료된_경매의_입찰내역을_구매자가_조회하면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.WON, LocalDateTime.now().minusMinutes(10));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertThatThrownBy(() -> bidService.getAuctionBids(1L, 2L, new GetAuctionBidsRequest(null, 20)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ENDED_AUCTION_BIDS_SELLER_ONLY.getMessage());
    then(bidRepository).should(never()).findAllByAuctionId(anyLong(), any(), anyInt());
  }

  @Test
  void 유찰된_경매의_입찰내역을_비로그인_조회자가_조회하면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.PASSED, LocalDateTime.now().minusMinutes(10));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertThatThrownBy(
            () -> bidService.getAuctionBids(1L, null, new GetAuctionBidsRequest(null, 20)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ENDED_AUCTION_BIDS_SELLER_ONLY.getMessage());
  }

  private Auction createAuction(
      Long auctionId, Long sellerMemberId, AuctionStatus status, LocalDateTime endedAt) {
    Consignment consignment =
        Consignment.builder()
            .sellerMember(createMember(sellerMemberId))
            .status(ConsignmentStatus.IN_AUCTION)
            .build();
    ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
    Auction auction =
        Auction.builder()
            .title("테스트 제목")
            .description("테스트 설명")
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(endedAt)
            .auctionStatus(status)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
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
}
