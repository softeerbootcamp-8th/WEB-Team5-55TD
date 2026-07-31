package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_ENDED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_STARTED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
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
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import java.time.LocalDateTime;
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

  private BidService bidService;

  @BeforeEach
  void setUp() {
    bidService = new BidService(auctionRepository, bidRepository, memberRepository);
  }

  @Test
  void 첫_입찰가가_시작가와_최소단위의_합이면_최고입찰로_저장된다() {
    // given
    Auction auction =
        createAuction(1L, 1L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    Member bidder = createMember(2L);
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
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
    PlaceBidResponse response = bidService.placeBid(1L, 2L, new PlaceBidRequest(10_500L));

    // then
    assertThat(response.bidId()).isEqualTo(10L);
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.memberId()).isEqualTo(2L);
    assertThat(response.bidPrice()).isEqualTo(10_500L);
    assertThat(response.bidStatus()).isEqualTo(BidStatus.HIGHEST);
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
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(3L)).willReturn(Optional.of(newBidder));
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
    Member bidder = createMember(2L);
    Bid previousHighestBid = Bid.create(auction, createMember(3L), 10_500L);
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.of(previousHighestBid));

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
    given(auctionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.of(previousHighestBid));

    // when & then
    assertExceptionCode(
        () -> bidService.placeBid(1L, 2L, new PlaceBidRequest(10_999L)), BELOW_MIN_INCREMENT);
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
