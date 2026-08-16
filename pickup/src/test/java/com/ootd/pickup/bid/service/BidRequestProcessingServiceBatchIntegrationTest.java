package com.ootd.pickup.bid.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidJpaRepository;
import com.ootd.pickup.bid.repository.BidRequestJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointJpaRepository;
import com.ootd.pickup.point.repository.PointReservationJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 같은 그룹(경매) 배치를 트랜잭션 하나로 몰아 처리하는 {@link BidRequestProcessingService#placeBidsForGroup}을 실제 DB(H2)로
 * 검증한다. Mockito만으로는 "트랜잭션이 롤백되지 않았다"를 확인할 수 없어 별도 통합 테스트로 분리했다.
 *
 * <p>재전달 중복({@link org.springframework.dao.DataIntegrityViolationException} 발생 시 건별 재시도로 폴백하는 경로)은
 * 이 클래스가 아니라 {@link BidRequestProcessingServiceTest}(Mockito 단위 테스트)에서 검증한다 - 이 테스트가 쓰는 H2는 {@code
 * flyway.enabled=false} + {@code ddl-auto=create-drop}으로 뜨는데, {@code bid.bid_request_id} 유니크
 * 제약(V9.5 마이그레이션의 {@code uk_bid_bid_request_id})은 Flyway 마이그레이션에만 있고 엔티티 매핑에는 의도적으로 없어(Bid 엔티티 자체
 * javadoc 참고) 이 환경에서 재현되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidRequestProcessingServiceBatchIntegrationTest {

  @Autowired private BidRequestProcessingService processingService;
  @Autowired private BidRequestJpaRepository bidRequestJpaRepository;
  @Autowired private BidJpaRepository bidJpaRepository;
  @Autowired private AuctionJpaRepository auctionJpaRepository;
  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;
  @Autowired private MemberJpaRepository memberJpaRepository;
  @Autowired private PointJpaRepository pointJpaRepository;
  @Autowired private PointReservationJpaRepository pointReservationJpaRepository;
  @Autowired private CardJpaRepository cardJpaRepository;

  @AfterEach
  void tearDown() {
    pointReservationJpaRepository.deleteAll();
    bidJpaRepository.deleteAll();
    bidRequestJpaRepository.deleteAll();
    auctionJpaRepository.deleteAll();
    consignmentJpaRepository.deleteAll();
    cardJpaRepository.deleteAll();
    pointJpaRepository.deleteAll();
    memberJpaRepository.deleteAll();
  }

  @Test
  void 같은_그룹_배치가_전부_성공하면_트랜잭션_하나로_전부_반영된다() {
    // given
    Auction auction = createAuction(10_000L, 500L);
    Member bidder1 = createBidderWithBalance("bidder-1", 100_000L);
    Member bidder2 = createBidderWithBalance("bidder-2", 100_000L);
    Member bidder3 = createBidderWithBalance("bidder-3", 100_000L);
    BidRequest request1 = saveBidRequest(auction.getAuctionId(), bidder1.getMemberId(), 10_500L);
    BidRequest request2 = saveBidRequest(auction.getAuctionId(), bidder2.getMemberId(), 11_000L);
    BidRequest request3 = saveBidRequest(auction.getAuctionId(), bidder3.getMemberId(), 11_500L);
    List<BidRequestCreatedMessageQueueEvent> events =
        List.of(eventOf(request1, bidder1), eventOf(request2, bidder2), eventOf(request3, bidder3));

    // when
    List<BidRequestCreatedMessageQueueEvent> done = processingService.placeBidsForGroup(events);

    // then
    assertThat(done).isEqualTo(events);
    assertThat(statusOf(request1)).isEqualTo(BidRequestStatus.SUCCEEDED);
    assertThat(statusOf(request2)).isEqualTo(BidRequestStatus.SUCCEEDED);
    assertThat(statusOf(request3)).isEqualTo(BidRequestStatus.SUCCEEDED);
    Auction updated = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(updated.getWinningPrice()).isEqualTo(11_500L);
  }

  @Test
  void 중간_요청이_업무상_실패해도_트랜잭션이_롤백되지_않고_나머지는_반영된다() {
    // given — request2는 request1이 이미 올려놓은 현재가보다 낮아 최소 입찰 단위 미만으로 거부된다
    Auction auction = createAuction(10_000L, 500L);
    Member bidder1 = createBidderWithBalance("bidder-1", 100_000L);
    Member bidder2 = createBidderWithBalance("bidder-2", 100_000L);
    Member bidder3 = createBidderWithBalance("bidder-3", 100_000L);
    BidRequest request1 = saveBidRequest(auction.getAuctionId(), bidder1.getMemberId(), 10_500L);
    BidRequest request2 = saveBidRequest(auction.getAuctionId(), bidder2.getMemberId(), 10_500L);
    BidRequest request3 = saveBidRequest(auction.getAuctionId(), bidder3.getMemberId(), 11_000L);
    List<BidRequestCreatedMessageQueueEvent> events =
        List.of(eventOf(request1, bidder1), eventOf(request2, bidder2), eventOf(request3, bidder3));

    // when
    List<BidRequestCreatedMessageQueueEvent> done = processingService.placeBidsForGroup(events);

    // then — 셋 다 종결 상태이므로(트랜잭션이 통째로 롤백됐다면 request1도 다시 PENDING일 것) 롤백되지 않았음을 알 수 있다
    assertThat(done).isEqualTo(events);
    assertThat(statusOf(request1)).isEqualTo(BidRequestStatus.SUCCEEDED);
    assertThat(statusOf(request2)).isEqualTo(BidRequestStatus.FAILED);
    assertThat(
            bidRequestJpaRepository
                .findById(request2.getBidRequestId())
                .orElseThrow()
                .getFailureCode())
        .isNotBlank();
    assertThat(statusOf(request3)).isEqualTo(BidRequestStatus.SUCCEEDED);
    Auction updated = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(updated.getWinningPrice()).isEqualTo(11_000L);
  }

  // 재전달 중복(DataIntegrityViolationException) 시 건별 재시도로 폴백하는 경로는
  // BidRequestProcessingServiceTest(Mockito 단위 테스트)에서 검증한다 - 이 통합 테스트가 쓰는 H2는
  // flyway.enabled=false + ddl-auto=create-drop 로 뜨는데, bid.bid_request_id 유니크
  // 제약(uk_bid_bid_request_id)은
  // Flyway 마이그레이션에만 있고 엔티티 매핑에는 없어(V9.5, 의도적 - Bid 엔티티 자체 javadoc 참고) 이 환경에서 재현되지 않는다.

  private BidRequestStatus statusOf(BidRequest bidRequest) {
    return bidRequestJpaRepository.findById(bidRequest.getBidRequestId()).orElseThrow().getStatus();
  }

  private BidRequest saveBidRequest(Long auctionId, Long memberId, Long bidPrice) {
    return bidRequestJpaRepository.save(BidRequest.create(auctionId, memberId, bidPrice));
  }

  private BidRequestCreatedMessageQueueEvent eventOf(BidRequest bidRequest, Member member) {
    return new BidRequestCreatedMessageQueueEvent(
        "event-" + bidRequest.getBidRequestId(),
        bidRequest.getBidRequestId(),
        bidRequest.getAuctionId(),
        member.getMemberId(),
        bidRequest.getBidPrice(),
        LocalDateTime.now());
  }

  private Member createBidderWithBalance(String loginId, long balance) {
    Member member = memberJpaRepository.save(Member.create(loginId, "password", loginId));
    Point point = Point.create(member.getMemberId());
    point.increaseBalance(balance);
    pointJpaRepository.save(point);
    return member;
  }

  private Auction createAuction(long startingPrice, long bidIncrement) {
    Member seller = memberJpaRepository.save(Member.create("seller-batch", "password", "판매자"));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("배치 테스트 카드")
                .cardNumber("batch-1")
                .setName("배치 테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.RARE_HOLO)
                .imageUrl("https://example.com/card.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .status(ConsignmentStatus.IN_AUCTION)
                .build());
    return auctionJpaRepository.saveAndFlush(
        Auction.builder()
            .title("배치 테스트 경매")
            .description("배치 테스트 설명")
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(startingPrice)
            .reservePrice(startingPrice + 5_000L)
            .bidIncrement(bidIncrement)
            .build());
  }
}
