package com.ootd.pickup.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
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
class AsyncBidRecorderTest {

  @Mock private AuctionRepository auctionRepository;

  @Mock private BidRepository bidRepository;

  @Mock private MemberRepository memberRepository;

  private AsyncBidRecorder asyncBidRecorder;

  @BeforeEach
  void setUp() {
    asyncBidRecorder = new AsyncBidRecorder(auctionRepository, bidRepository, memberRepository);
  }

  @Test
  void 이전_최고입찰이_없으면_새_입찰만_최고입찰로_저장한다() {
    // given
    Auction auction = createAuction(1L);
    Member bidder = createMember(2L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(bidder));
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.empty());

    // when
    asyncBidRecorder.recordBid(1L, 2L, 10_500L);

    // then
    then(bidRepository)
        .should()
        .save(
            argThat(
                bid ->
                    bid.getBidPrice().equals(10_500L) && bid.getBidStatus() == BidStatus.HIGHEST));
  }

  @Test
  void 이전_최고입찰이_있으면_추월처리_후_새_입찰을_저장한다() {
    // given
    Auction auction = createAuction(1L);
    Member previousBidder = createMember(3L);
    Member newBidder = createMember(2L);
    Bid previousHighestBid = Bid.create(auction, previousBidder, 10_000L);
    given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
    given(memberRepository.findById(2L)).willReturn(Optional.of(newBidder));
    given(bidRepository.findFirstByAuctionIdAndBidStatus(1L, BidStatus.HIGHEST))
        .willReturn(Optional.of(previousHighestBid));

    // when
    asyncBidRecorder.recordBid(1L, 2L, 10_500L);

    // then
    assertThat(previousHighestBid.getBidStatus()).isEqualTo(BidStatus.OUTBID);
    then(bidRepository).should().save(previousHighestBid);
    then(bidRepository).should(times(2)).save(any(Bid.class));
  }

  @Test
  void 경매를_찾을_수_없어도_예외를_전파하지_않고_로그만_남긴다() {
    // given
    given(auctionRepository.findById(1L)).willReturn(Optional.empty());

    // when & then
    assertThatCode(() -> asyncBidRecorder.recordBid(1L, 2L, 10_500L)).doesNotThrowAnyException();
    then(bidRepository).should(never()).save(any(Bid.class));
  }

  private Auction createAuction(Long auctionId) {
    Consignment consignment =
        Consignment.builder()
            .sellerMember(createMember(99L))
            .status(ConsignmentStatus.AUCTION_SCHEDULED)
            .build();
    ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
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
    Member member = Member.create("loginId" + memberId, "password", "닉네임" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }
}
