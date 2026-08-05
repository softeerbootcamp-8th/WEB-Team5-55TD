package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_ENDED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_STARTED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.ILLEGAL_ARGUMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_CURSOR;
import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.dto.request.GetAuctionBidsRequest;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.AuctionBidListItemResponse;
import com.ootd.pickup.bid.dto.response.PlaceBidAcceptedResponse;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.repository.BidPriceCacheRepository;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

  @Mock private AuctionRepository auctionRepository;

  @Mock private BidRepository bidRepository;

  @Mock private MemberRepository memberRepository;

  @Mock private BidPriceCacheRepository bidPriceCacheRepository;

  @Mock private AsyncBidRecorder asyncBidRecorder;

  private BidService bidService;

  @BeforeEach
  void setUp() {
    bidService =
        new BidService(
            auctionRepository,
            bidRepository,
            memberRepository,
            bidPriceCacheRepository,
            asyncBidRecorder);
  }

  @Test
  void 분산락_방식_첫_입찰가가_시작가와_최소단위의_합이면_최고입찰로_저장된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.empty());
    given(bidRepository.save(any(Bid.class)))
        .willAnswer(
            invocation -> {
              Bid bid = invocation.getArgument(0);
              ReflectionTestUtils.setField(bid, "bidId", 10L);
              return bid;
            });

    // when
    PlaceBidResponse response =
        bidService.placeBidWithDistributedLock(1L, 2L, new PlaceBidRequest(10_500L));

    // then
    assertThat(response.bidId()).isEqualTo(10L);
    assertThat(response.bidStatus()).isEqualTo(BidStatus.HIGHEST);
    then(bidPriceCacheRepository).shouldHaveNoInteractions();
  }

  @Test
  void 분산락_방식_현재가_이하로_입찰하면_추월_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Bid previousHighestBid = Bid.create(auction, createMember(3L), 10_500L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(createMember(2L)));
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.of(previousHighestBid));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBidWithDistributedLock(1L, 2L, new PlaceBidRequest(10_500L)),
        OUTBID_EXISTS);
    then(bidRepository).should(never()).save(any(Bid.class));
  }

  @Test
  void 조건부UPDATE_방식_첫_입찰가가_시작가와_최소단위의_합이면_최고입찰로_저장된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 10_500L)).willReturn(1);
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.empty());
    given(bidRepository.save(any(Bid.class)))
        .willAnswer(
            invocation -> {
              Bid bid = invocation.getArgument(0);
              ReflectionTestUtils.setField(bid, "bidId", 10L);
              return bid;
            });

    // when
    PlaceBidResponse response =
        bidService.placeBidWithConditionalUpdate(1L, 2L, new PlaceBidRequest(10_500L));

    // then
    assertThat(response.bidId()).isEqualTo(10L);
    assertThat(response.bidStatus()).isEqualTo(BidStatus.HIGHEST);
    then(bidPriceCacheRepository).shouldHaveNoInteractions();
  }

  @Test
  void 조건부UPDATE_방식_동시_입찰로_현재가가_이미_갱신되었으면_추월_예외가_발생하고_경매를_다시_조회하지_않는다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 10_500L)).willReturn(0);

    // when & then
    assertExceptionCode(
        () -> bidService.placeBidWithConditionalUpdate(1L, 2L, new PlaceBidRequest(10_500L)),
        OUTBID_EXISTS);
    then(bidRepository).should(never()).save(any(Bid.class));
    then(bidPriceCacheRepository).shouldHaveNoInteractions();
    then(auctionRepository).should().findById(1L);
  }

  @Test
  void 첫_입찰가가_시작가와_최소단위의_합이면_최고입찰로_저장된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 10_500L)).willReturn(1);
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.empty());
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
    then(bidPriceCacheRepository).should().saveCurrentPrice(1L, 10_500L);
  }

  @Test
  void 더_높은_입찰이_성공하면_기존_최고입찰은_추월상태가_된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    ReflectionTestUtils.setField(auction, "currentPrice", 10_500L);
    Member previousBidder = createMember(2L);
    Member newBidder = createMember(3L);
    Bid previousHighestBid = Bid.create(auction, previousBidder, 10_500L);
    ReflectionTestUtils.setField(previousHighestBid, "bidId", 10L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(3L)).willReturn(Optional.of(newBidder));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 11_000L)).willReturn(1);
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.of(previousHighestBid));
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
  }

  @Test
  void 현재가_이하로_입찰하면_더_높은_입찰_존재_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    ReflectionTestUtils.setField(auction, "currentPrice", 10_500L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

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
    ReflectionTestUtils.setField(auction, "currentPrice", 10_500L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_999L)), BELOW_MIN_INCREMENT);
  }

  @Test
  void 동시_입찰로_현재가가_이미_갱신되었으면_추월_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Auction latestAuction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    ReflectionTestUtils.setField(latestAuction, "currentPrice", 11_000L);
    Member bidder = createMember(2L);
    given(auctionRepository.findById(1L))
        .willReturn(Optional.of(auction), Optional.of(latestAuction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 10_500L)).willReturn(0);

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), OUTBID_EXISTS);
    then(bidRepository).should(never()).save(any(Bid.class));
    then(bidPriceCacheRepository).should().saveCurrentPrice(1L, 11_000L);
  }

  @Test
  void 동시_입찰로_최소단위_조건을_충족하지_못하면_최소단위_미만_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Auction latestAuction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    ReflectionTestUtils.setField(latestAuction, "currentPrice", 10_500L);
    Member bidder = createMember(2L);
    given(auctionRepository.findById(1L))
        .willReturn(Optional.of(auction), Optional.of(latestAuction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 10_600L)).willReturn(0);

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_600L)), BELOW_MIN_INCREMENT);
    then(bidRepository).should(never()).save(any(Bid.class));
  }

  @Test
  void 캐시된_현재가_이하로_입찰하면_DB조회_없이_추월_예외가_발생한다() {
    // given
    given(bidPriceCacheRepository.findCurrentPrice(1L)).willReturn(Optional.of(10_500L));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), OUTBID_EXISTS);
    then(auctionRepository).shouldHaveNoInteractions();
    then(memberRepository).shouldHaveNoInteractions();
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 캐시된_현재가보다_높은_입찰이면_평소처럼_DB_검증까지_진행된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    ReflectionTestUtils.setField(auction, "currentPrice", 10_500L);
    Member bidder = createMember(2L);
    given(bidPriceCacheRepository.findCurrentPrice(1L)).willReturn(Optional.of(10_500L));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 11_000L)).willReturn(1);
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.empty());
    given(bidRepository.save(any(Bid.class)))
        .willAnswer(
            invocation -> {
              Bid bid = invocation.getArgument(0);
              ReflectionTestUtils.setField(bid, "bidId", 12L);
              return bid;
            });

    // when
    PlaceBidResponse response = bidService.placeBid(1L, 2L, new PlaceBidRequest(11_000L));

    // then
    assertThat(response.bidId()).isEqualTo(12L);
    then(auctionRepository).should().updateCurrentPriceIfHigher(1L, 11_000L);
    then(bidPriceCacheRepository).should().saveCurrentPrice(1L, 11_000L);
  }

  @Test
  void 짧은트랜잭션_방식_입찰에_성공하면_캐시를_갱신하고_입찰기록은_비동기로_넘긴다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    given(bidPriceCacheRepository.findCurrentPrice(1L)).willReturn(Optional.empty());
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 10_500L)).willReturn(1);

    // when
    PlaceBidAcceptedResponse response =
        bidService.placeBidWithShortTransaction(1L, 2L, new PlaceBidRequest(10_500L));

    // then
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.memberId()).isEqualTo(2L);
    assertThat(response.bidPrice()).isEqualTo(10_500L);
    then(bidPriceCacheRepository).should().saveCurrentPrice(1L, 10_500L);
    then(asyncBidRecorder).should().recordBid(1L, 2L, 10_500L);
    then(memberRepository).shouldHaveNoInteractions();
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 짧은트랜잭션_방식_캐시된_현재가_이하로_입찰하면_DB조회_없이_추월_예외가_발생한다() {
    // given
    given(bidPriceCacheRepository.findCurrentPrice(1L)).willReturn(Optional.of(10_500L));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBidWithShortTransaction(1L, 2L, new PlaceBidRequest(10_500L)),
        OUTBID_EXISTS);
    then(auctionRepository).shouldHaveNoInteractions();
    then(asyncBidRecorder).shouldHaveNoInteractions();
  }

  @Test
  void 짧은트랜잭션_방식_동시_입찰로_현재가가_이미_갱신되었으면_추월_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    given(bidPriceCacheRepository.findCurrentPrice(1L)).willReturn(Optional.empty());
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(auctionRepository.updateCurrentPriceIfHigher(1L, 10_500L)).willReturn(0);

    // when & then
    assertExceptionCode(
        () -> bidService.placeBidWithShortTransaction(1L, 2L, new PlaceBidRequest(10_500L)),
        OUTBID_EXISTS);
    then(bidPriceCacheRepository).should(never()).saveCurrentPrice(any(), any());
    then(asyncBidRecorder).shouldHaveNoInteractions();
  }

  @Test
  void 시작되지_않은_경매에_입찰하면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.SCHEDULED, LocalDateTime.now().plusHours(1));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), AUCTION_NOT_STARTED);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 종료된_상태의_경매에_입찰하면_예외가_발생한다() {
    // given
    Auction auction = createAuction(1L, 1L, AuctionStatus.WON, LocalDateTime.now().minusMinutes(1));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), AUCTION_ENDED);
  }

  @Test
  void 진행상태여도_종료시간이_지났으면_입찰할_수_없다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().minusMinutes(1));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L)), AUCTION_ENDED);
  }

  @Test
  void 판매자가_본인의_경매에_입찰하면_예외가_발생한다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 1L, new PlaceBidRequest(10_500L)),
        AUCTION_SELLER_BID_FORBIDDEN);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 존재하지_않는_경매에_입찰하면_예외가_발생한다() {
    // given
    given(auctionRepository.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(999L, 2L, new PlaceBidRequest(10_500L)), AUCTION_NOT_FOUND);
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 경매_입찰_내역을_조회하면_최근_입찰_순으로_마스킹된_닉네임과_함께_반환된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member viewer = createMember(2L);
    Member other = createMember(3L);
    Bid myBid = createBid(auction, viewer, 11_000L, 101L);
    Bid otherBid = createBid(auction, other, 10_500L, 100L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(bidRepository.findAllByAuctionId(1L, null, 21)).willReturn(List.of(myBid, otherBid));

    // when
    CursorPageResponse<AuctionBidListItemResponse, String> response =
        bidService.getAuctionBids(1L, 2L, new GetAuctionBidsRequest(null, 20));

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.cursor()).isNull();
    assertThat(response.items()).hasSize(2);

    AuctionBidListItemResponse first = response.items().get(0);
    assertThat(first.bidId()).isEqualTo(101L);
    assertThat(first.nicknameMasked()).isEqualTo("닉네임***임2");
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

  private Auction createAuction(
      Long auctionId, Long sellerMemberId, AuctionStatus status, LocalDateTime endedAt) {
    Consignment consignment =
        Consignment.builder()
            .sellerMember(createMember(sellerMemberId))
            .status(ConsignmentStatus.AUCTION_SCHEDULED)
            .build();
    ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
    Auction auction =
        Auction.builder()
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
