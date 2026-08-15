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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

  @Autowired private BidService bidService;

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
  void 이전_최고입찰자와_겹치는_동시_입찰이_여러_라운드_반복돼도_교착상태_없이_최고가만_남는다() throws Exception {
    // given
    // PointLockService.lockPoints가 memberId 오름차순으로 두 회원(신규 입찰자 + 직전 최고입찰자)의 포인트를
    // 한 번의 조회로 묶어 잠그도록 바뀌었다. 매 라운드 승자가 다음 라운드에도 참여해 "겹치는 회원 조합"을
    // 반복적으로 만들어, 배치 락의 획득 순서가 흔들려도 교착상태 없이 항상 더 높은 입찰만 남는지 검증한다.
    Member seller = memberJpaRepository.save(Member.create("seller-c", "password", "판매자C"));
    Member memberA = memberJpaRepository.save(Member.create("bidder-a", "password", "입찰자A"));
    Member memberB = memberJpaRepository.save(Member.create("bidder-b", "password", "입찰자B"));
    Member memberC = memberJpaRepository.save(Member.create("bidder-c", "password", "입찰자C"));
    pointJpaRepository.save(createPointWithBalance(memberA.getMemberId(), 1_000_000L));
    pointJpaRepository.save(createPointWithBalance(memberB.getMemberId(), 1_000_000L));
    pointJpaRepository.save(createPointWithBalance(memberC.getMemberId(), 1_000_000L));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("테스트 카드 C")
                .cardNumber("002")
                .setName("테스트 세트 C")
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
                .title("테스트 제목 C")
                .description("테스트 설명 C")
                .consignment(consignment)
                .startedAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .auctionStatus(AuctionStatus.ONGOING)
                .startingPrice(10_000L)
                .reservePrice(15_000L)
                .bidIncrement(500L)
                .build());

    List<Member> bidders = List.of(memberA, memberB, memberC);
    int rounds = 5;
    long lastHigherBid = 0L;

    // when
    // 매 라운드 두 입찰가를 "그 순간의 실제 현재가"를 다시 조회해 그보다 확실히 높게 정한다(동률/근접값에
    // 기대지 않는다) - 어느 스레드가 먼저 처리되든 두 입찰 모두 그 시점 현재가보다 높아 한쪽은 성공하고,
    // 더 낮은 쪽이 나중에 처리되면 정상적으로 추월당해 실패한다. 더 높은 입찰가가 항상 최종 승자가 되므로
    // 다음 라운드의 "직전 최고입찰자"는 항상 이번 라운드의 더 높은 입찰자다.
    for (int round = 0; round < rounds; round++) {
      Member x = bidders.get(round % bidders.size());
      Member y = bidders.get((round + 1) % bidders.size());
      long basePrice =
          auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow().getCurrentPrice();
      long lowerBid = basePrice + 500L;
      long higherBid = basePrice + 1_000L;
      lastHigherBid = higherBid;

      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
        Future<String> lowerResult =
            executorService.submit(
                () ->
                    placeBidAfterSignal(
                        auction.getAuctionId(), x.getMemberId(), lowerBid, ready, start));
        Future<String> higherResult =
            executorService.submit(
                () ->
                    placeBidAfterSignal(
                        auction.getAuctionId(), y.getMemberId(), higherBid, ready, start));
        ready.await();
        start.countDown();
        List<String> results = List.of(lowerResult.get(), higherResult.get());

        // then (라운드마다) - 데드락으로 스레드가 멈추지 않고 둘 다 완료되며, 더 높은 입찰이 항상 이긴다
        assertThat(results).allMatch(Set.of("SUCCESS", "OUTBID_EXISTS")::contains);
      }

      Auction afterRound = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
      assertThat(afterRound.getWinningPrice()).isEqualTo(higherBid);
    }

    Auction updatedAuction = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(updatedAuction.getWinningPrice()).isEqualTo(lastHigherBid);
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
