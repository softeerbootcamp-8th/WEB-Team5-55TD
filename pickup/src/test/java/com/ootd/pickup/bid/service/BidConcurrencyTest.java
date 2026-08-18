package com.ootd.pickup.bid.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.repository.BidJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointJpaRepository;
import com.ootd.pickup.point.repository.PointReservationJpaRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

  private static final int CONCURRENT_FIRST_BIDS = 10;
  private static final int FIRST_BID_ROUNDS = 20;

  @Autowired private BidService bidService;

  @Autowired private BidJpaRepository bidJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private PointJpaRepository pointJpaRepository;

  @Autowired private PointReservationJpaRepository pointReservationJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    pointReservationJpaRepository.deleteAll();
    jdbcTemplate.update("update auction set winning_bid_id = null");
    bidJpaRepository.deleteAll();
    auctionJpaRepository.deleteAll();
    consignmentJpaRepository.deleteAll();
    cardJpaRepository.deleteAll();
    pointJpaRepository.deleteAll();
    memberJpaRepository.deleteAll();
  }

  @Test
  void 동시에_입찰해도_가장_높은_입찰만_최고상태로_남는다() throws Exception {
    // given
    Member seller = memberJpaRepository.save(Member.create("seller", "password", "판매자"));
    Member firstBidder = memberJpaRepository.save(Member.create("bidder1", "password", "입찰자1"));
    Member secondBidder = memberJpaRepository.save(Member.create("bidder2", "password", "입찰자2"));
    pointJpaRepository.save(createPointWithBalance(firstBidder.getMemberId(), 50_000L));
    pointJpaRepository.save(createPointWithBalance(secondBidder.getMemberId(), 50_000L));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("테스트 카드")
                .cardNumber("001")
                .setName("테스트 세트")
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
    Auction auction =
        auctionJpaRepository.saveAndFlush(
            Auction.builder()
                .title("테스트 제목")
                .description("테스트 설명")
                .consignment(consignment)
                .startedAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .auctionStatus(AuctionStatus.ONGOING)
                .startingPrice(10_000L)
                .reservePrice(15_000L)
                .bidIncrement(500L)
                .build());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    // when
    List<String> results;
    try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
      Future<String> firstResult =
          executorService.submit(
              () ->
                  placeBidAfterSignal(
                      auction.getAuctionId(), firstBidder.getMemberId(), 10_500L, ready, start));
      Future<String> secondResult =
          executorService.submit(
              () ->
                  placeBidAfterSignal(
                      auction.getAuctionId(), secondBidder.getMemberId(), 11_000L, ready, start));
      ready.await();
      start.countDown();
      results = List.of(firstResult.get(), secondResult.get());
    }

    // then
    // 정확히 하나의 입찰만 HIGHEST로 남는다는 보장은 이제 데이터 모델 자체가 구조적으로 지킨다
    // (Bid.getBidStatus()는 auction.winningBidId와 같은 입찰인지로만 판단하므로, 어떤 순간에도
    // winningBidId와 같은 bidId를 가진 입찰은 최대 하나다). 여기서 실제로 검증해야 하는 것은
    // 동시에 들어온 두 입찰 중 더 높은 쪽이 비관적 락으로 안전하게 직렬화되어 최종 승자가 됐는지다.
    Auction updatedAuction = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(results).allMatch(Set.of("SUCCESS", "OUTBID_EXISTS")::contains);
    assertThat(updatedAuction.getWinningPrice()).isEqualTo(11_000L);
  }

  @Test
  void 서로_다른_경매에_첫입찰을_동시에_반복해도_모두_성공한다() throws Exception {
    // given
    Member seller = memberJpaRepository.save(Member.create("firstBidSeller", "password", "판매자"));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("동시 첫 입찰 테스트 카드")
                .cardNumber("002")
                .setName("테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.RARE_HOLO)
                .imageUrl("https://example.com/first-bid-card.png")
                .build());
    List<Long> bidderIds = new ArrayList<>();
    List<Long> auctionIds = new ArrayList<>();
    for (int index = 0; index < CONCURRENT_FIRST_BIDS * FIRST_BID_ROUNDS; index++) {
      Member bidder =
          memberJpaRepository.save(
              Member.create("firstBidder" + index, "password", "첫입찰자" + index));
      pointJpaRepository.save(createPointWithBalance(bidder.getMemberId(), 50_000L));
      Consignment consignment =
          consignmentJpaRepository.save(
              Consignment.builder()
                  .card(card)
                  .sellerMember(seller)
                  .status(ConsignmentStatus.IN_AUCTION)
                  .build());
      Auction auction =
          auctionJpaRepository.save(
              Auction.builder()
                  .title("동시 첫 입찰 경매 " + index)
                  .description("동시 첫 입찰 테스트")
                  .consignment(consignment)
                  .startedAt(LocalDateTime.now().minusHours(1))
                  .endedAt(LocalDateTime.now().plusHours(1))
                  .auctionStatus(AuctionStatus.ONGOING)
                  .startingPrice(10_000L)
                  .reservePrice(15_000L)
                  .bidIncrement(500L)
                  .build());
      bidderIds.add(bidder.getMemberId());
      auctionIds.add(auction.getAuctionId());
    }
    auctionJpaRepository.flush();

    // when
    List<String> results = new ArrayList<>();
    try (ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_FIRST_BIDS)) {
      for (int round = 0; round < FIRST_BID_ROUNDS; round++) {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_FIRST_BIDS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> roundResults = new ArrayList<>();
        int roundStart = round * CONCURRENT_FIRST_BIDS;
        for (int offset = 0; offset < CONCURRENT_FIRST_BIDS; offset++) {
          int index = roundStart + offset;
          roundResults.add(
              executorService.submit(
                  () ->
                      placeBidAfterSignal(
                          auctionIds.get(index), bidderIds.get(index), 10_500L, ready, start)));
        }
        ready.await();
        start.countDown();
        for (Future<String> result : roundResults) {
          results.add(result.get());
        }
      }
    }

    // then
    assertThat(results).hasSize(CONCURRENT_FIRST_BIDS * FIRST_BID_ROUNDS).containsOnly("SUCCESS");
    assertThat(bidJpaRepository.count()).isEqualTo(CONCURRENT_FIRST_BIDS * FIRST_BID_ROUNDS);
    assertThat(pointReservationJpaRepository.count())
        .isEqualTo(CONCURRENT_FIRST_BIDS * FIRST_BID_ROUNDS);
    assertThat(bidderIds)
        .allSatisfy(
            bidderId ->
                assertThat(pointJpaRepository.findByMemberId(bidderId).orElseThrow())
                    .extracting(Point::getReservedBalance, Point::getAvailableBalance)
                    .containsExactly(10_500L, 39_500L));
  }

  private Point createPointWithBalance(Long memberId, long balance) {
    Point point = Point.create(memberId);
    point.increaseBalance(balance);
    return point;
  }

  private String placeBidAfterSignal(
      Long auctionId, Long memberId, Long bidPrice, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    try {
      bidService.placeBid(auctionId, memberId, new PlaceBidRequest(bidPrice));
      return "SUCCESS";
    } catch (PickUpException exception) {
      return exception.getExceptionCodeName();
    }
  }
}
