package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_ENDED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_STARTED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.BID_REQUEST_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.INSUFFICIENT_BID_LIMIT;
import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ootd.pickup.auction.cache.AuctionSnapshot;
import com.ootd.pickup.auction.cache.AuctionSnapshotCache;
import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import com.ootd.pickup.bid.dto.response.BidRequestResultResponse;
import com.ootd.pickup.bid.dto.response.CreateBidRequestResponse;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidRequestServiceTest {

  @Mock private AuctionRepository auctionRepository;
  @Mock private BidRequestRepository bidRequestRepository;
  @Mock private EventProducer eventProducer;
  @Mock private AuctionSnapshotCache auctionSnapshotCache;
  @Mock private PointRepository pointRepository;

  private BidRequestService bidRequestService;

  @BeforeEach
  void setUp() {
    bidRequestService =
        new BidRequestService(
            auctionRepository,
            bidRequestRepository,
            eventProducer,
            auctionSnapshotCache,
            pointRepository);
  }

  @Test
  void 캐시가_있으면_DB_조회_없이_입찰_요청을_생성한다() {
    // given
    given(auctionSnapshotCache.find(1L))
        .willReturn(Optional.of(createSnapshot(1L, 10_000L, 500L, AuctionStatus.ONGOING, 2L, 9L)));
    given(pointRepository.findByMemberId(2L))
        .willReturn(Optional.of(pointWithBalance(2L, 20_000L)));
    given(bidRequestRepository.save(any(BidRequest.class)))
        .willAnswer(
            invocation -> {
              BidRequest bidRequest = invocation.getArgument(0);
              ReflectionTestUtils.setField(bidRequest, "bidRequestId", 10L);
              return bidRequest;
            });

    // when
    CreateBidRequestResponse response = bidRequestService.createBidRequest(1L, 2L, 10_500L);

    // then
    assertThat(response.bidRequestId()).isEqualTo(10L);
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.memberId()).isEqualTo(2L);
    assertThat(response.bidPrice()).isEqualTo(10_500L);
    assertThat(response.status()).isEqualTo(BidRequestStatus.PENDING);
    then(auctionRepository).should(never()).findById(any());
    then(auctionSnapshotCache).should(never()).put(any());

    ArgumentCaptor<BidRequestCreatedMessageQueueEvent> eventCaptor =
        ArgumentCaptor.forClass(BidRequestCreatedMessageQueueEvent.class);
    then(eventProducer).should().produce(eventCaptor.capture());
    assertThat(eventCaptor.getValue().bidRequestId()).isEqualTo(10L);
  }

  @Test
  void 캐시가_없으면_DB에서_조회해_캐시를_채우고_입찰_요청을_생성한다() {
    // given
    Auction auction =
        createAuction(
            1L, 9L, AuctionStatus.ONGOING, 10_000L, 500L, LocalDateTime.now().plusHours(1));
    given(auctionSnapshotCache.find(1L)).willReturn(Optional.empty());
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(pointRepository.findByMemberId(2L))
        .willReturn(Optional.of(pointWithBalance(2L, 20_000L)));
    given(bidRequestRepository.save(any(BidRequest.class)))
        .willAnswer(
            invocation -> {
              BidRequest bidRequest = invocation.getArgument(0);
              ReflectionTestUtils.setField(bidRequest, "bidRequestId", 10L);
              return bidRequest;
            });

    // when
    CreateBidRequestResponse response = bidRequestService.createBidRequest(1L, 2L, 10_500L);

    // then
    assertThat(response.bidRequestId()).isEqualTo(10L);
    ArgumentCaptor<AuctionSnapshot> snapshotCaptor = ArgumentCaptor.forClass(AuctionSnapshot.class);
    then(auctionSnapshotCache).should().put(snapshotCaptor.capture());
    assertThat(snapshotCaptor.getValue().auctionId()).isEqualTo(1L);
    assertThat(snapshotCaptor.getValue().currentPrice()).isEqualTo(10_000L);
    assertThat(snapshotCaptor.getValue().sellerMemberId()).isEqualTo(9L);
  }

  @Test
  void 캐시도_DB에도_없는_경매면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(999L)).willReturn(Optional.empty());
    given(auctionRepository.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertExceptionCode(
        () -> bidRequestService.createBidRequest(999L, 2L, 10_500L), AUCTION_NOT_FOUND);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 캐시가_시작전_경매를_가리키면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(1L))
        .willReturn(Optional.of(createSnapshot(1L, null, 500L, AuctionStatus.SCHEDULED, 2L, 9L)));

    // when & then
    assertExceptionCode(
        () -> bidRequestService.createBidRequest(1L, 2L, 10_500L), AUCTION_NOT_STARTED);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 캐시가_종료된_경매를_가리키면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(1L)).willReturn(createWonSnapshot(1L, 11_000L, 500L, 2L, 9L));

    // when & then
    assertExceptionCode(() -> bidRequestService.createBidRequest(1L, 2L, 11_500L), AUCTION_ENDED);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 캐시가_판매자_본인을_가리키면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(1L))
        .willReturn(Optional.of(createSnapshot(1L, 10_000L, 500L, AuctionStatus.ONGOING, 9L, 9L)));

    // when & then
    assertExceptionCode(
        () -> bidRequestService.createBidRequest(1L, 9L, 10_500L), AUCTION_SELLER_BID_FORBIDDEN);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 캐시된_현재가_이하로_입찰하면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(1L))
        .willReturn(Optional.of(createSnapshot(1L, 10_500L, 500L, AuctionStatus.ONGOING, 2L, 9L)));

    // when & then
    assertExceptionCode(() -> bidRequestService.createBidRequest(1L, 2L, 10_500L), OUTBID_EXISTS);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 캐시된_최소_증가폭_미만이면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(1L))
        .willReturn(Optional.of(createSnapshot(1L, 10_000L, 500L, AuctionStatus.ONGOING, 2L, 9L)));

    // when & then
    assertExceptionCode(
        () -> bidRequestService.createBidRequest(1L, 2L, 10_400L), BELOW_MIN_INCREMENT);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 보유_포인트보다_큰_금액을_입찰하면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(1L))
        .willReturn(Optional.of(createSnapshot(1L, 10_000L, 500L, AuctionStatus.ONGOING, 2L, 9L)));
    given(pointRepository.findByMemberId(2L))
        .willReturn(Optional.of(pointWithBalance(2L, 10_000L)));

    // when & then
    assertExceptionCode(
        () -> bidRequestService.createBidRequest(1L, 2L, 10_500L), INSUFFICIENT_BID_LIMIT);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 포인트_정보가_없으면_예외가_발생하고_요청이_생성되지_않는다() {
    // given
    given(auctionSnapshotCache.find(1L))
        .willReturn(Optional.of(createSnapshot(1L, 10_000L, 500L, AuctionStatus.ONGOING, 2L, 9L)));
    given(pointRepository.findByMemberId(2L)).willReturn(Optional.empty());

    // when & then
    assertExceptionCode(() -> bidRequestService.createBidRequest(1L, 2L, 10_500L), POINT_NOT_FOUND);
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }

  @Test
  void 본인의_입찰_요청이면_처리_결과를_조회한다() {
    BidRequest bidRequest = BidRequest.create(1L, 2L, 10_500L);
    ReflectionTestUtils.setField(bidRequest, "bidRequestId", 10L);
    bidRequest.succeed();
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(bidRequest));

    BidRequestResultResponse response = bidRequestService.getBidRequestResult(1L, 10L, 2L);

    assertThat(response.bidRequestId()).isEqualTo(10L);
    assertThat(response.status()).isEqualTo(BidRequestStatus.SUCCEEDED);
    assertThat(response.processedAt()).isNotNull();
  }

  @Test
  void 다른_회원의_입찰_요청은_찾을_수_없는_것처럼_응답한다() {
    BidRequest bidRequest = BidRequest.create(1L, 2L, 10_500L);
    ReflectionTestUtils.setField(bidRequest, "bidRequestId", 10L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(bidRequest));

    assertThatThrownBy(() -> bidRequestService.getBidRequestResult(1L, 10L, 3L))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(BID_REQUEST_NOT_FOUND.getClientExceptionCode().name()));
  }

  @Test
  void 다른_경매의_입찰_요청은_찾을_수_없는_것처럼_응답한다() {
    BidRequest bidRequest = BidRequest.create(1L, 2L, 10_500L);
    ReflectionTestUtils.setField(bidRequest, "bidRequestId", 10L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(bidRequest));

    assertThatThrownBy(() -> bidRequestService.getBidRequestResult(2L, 10L, 2L))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(BID_REQUEST_NOT_FOUND.getClientExceptionCode().name()));
  }

  private void assertExceptionCode(Runnable runnable, ExceptionCode exceptionCode) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(exceptionCode.getClientExceptionCode().name()));
  }

  private AuctionSnapshot createSnapshot(
      Long auctionId,
      Long currentPrice,
      Long bidIncrement,
      AuctionStatus status,
      Long memberId,
      Long sellerMemberId) {
    return new AuctionSnapshot(
        auctionId,
        currentPrice,
        bidIncrement,
        status,
        LocalDateTime.now().plusHours(1),
        sellerMemberId);
  }

  private Optional<AuctionSnapshot> createWonSnapshot(
      Long auctionId, Long currentPrice, Long bidIncrement, Long memberId, Long sellerMemberId) {
    return Optional.of(
        new AuctionSnapshot(
            auctionId,
            currentPrice,
            bidIncrement,
            AuctionStatus.WON,
            LocalDateTime.now().minusMinutes(1),
            sellerMemberId));
  }

  private Point pointWithBalance(Long memberId, long balance) {
    Point point = Point.create(memberId);
    point.increaseBalance(balance);
    return point;
  }

  private Auction createAuction(
      Long auctionId,
      Long sellerMemberId,
      AuctionStatus status,
      Long startingPrice,
      Long bidIncrement,
      LocalDateTime endedAt) {
    Member seller = Member.create("seller" + sellerMemberId, "password", "판매자" + sellerMemberId);
    ReflectionTestUtils.setField(seller, "memberId", sellerMemberId);
    Consignment consignment =
        Consignment.builder().sellerMember(seller).status(ConsignmentStatus.IN_AUCTION).build();
    ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
    Auction auction =
        Auction.builder()
            .title("테스트 제목")
            .description("테스트 설명")
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(endedAt)
            .auctionStatus(status)
            .startingPrice(startingPrice)
            .reservePrice(startingPrice + 5_000L)
            .bidIncrement(bidIncrement)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }
}
