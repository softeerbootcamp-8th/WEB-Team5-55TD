package com.ootd.pickup.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
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
import com.ootd.pickup.point.repository.PointTransactionJpaRepository;
import com.ootd.pickup.settlement.domain.Settlement;
import com.ootd.pickup.settlement.domain.SettlementType;
import com.ootd.pickup.settlement.repository.SettlementJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SettlementService의 정산 처리를 실제 DB 트랜잭션과 락으로 검증한다.
 *
 * <p>{@code SettlementServiceTest}는 Mockito로 락 획득 순서만 검증하지만, 여기서는 실제 비관적 락({@code
 * PointJpaRepository#findByMemberIdForUpdate})과 {@code settlement} 테이블의 유니크 제약이 걸린 채로 서로 다른 트랜잭션이
 * 동시에 부딛혔을 때도 교착상태 없이 끝나고 최종 데이터가 정확한지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SettlementConcurrencyTest {

  private static final long TIMEOUT_SECONDS = 10L;

  @Autowired private SettlementService settlementService;
  @Autowired private AuctionJpaRepository auctionJpaRepository;
  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;
  @Autowired private MemberJpaRepository memberJpaRepository;
  @Autowired private CardJpaRepository cardJpaRepository;
  @Autowired private PointJpaRepository pointJpaRepository;
  @Autowired private PointTransactionJpaRepository pointTransactionJpaRepository;
  @Autowired private SettlementJpaRepository settlementJpaRepository;

  @AfterEach
  void tearDown() {
    pointTransactionJpaRepository.deleteAll();
    settlementJpaRepository.deleteAll();
    pointJpaRepository.deleteAll();
    auctionJpaRepository.deleteAll();
    consignmentJpaRepository.deleteAll();
    cardJpaRepository.deleteAll();
    memberJpaRepository.deleteAll();
  }

  @Test
  void 같은_회원조합을_반대_역할로_동시에_정산해도_교착없이_끝나고_잔액과_정산건수가_정확하다() throws Exception {
    // given: memberA가 낙찰자인 경매와 memberB가 낙찰자인 경매를 동시에 정산한다.
    // 락 순서를 역할(낙찰자 먼저) 기준으로 잡으면 한쪽은 A→B, 다른 쪽은 B→A로 잠가 교착상태가 날 수 있다.
    Member memberA = memberJpaRepository.save(Member.create("memberA", "password", "회원A"));
    Member memberB = memberJpaRepository.save(Member.create("memberB", "password", "회원B"));
    pointJpaRepository.save(seedPoint(memberA.getMemberId(), 100_000L));
    pointJpaRepository.save(seedPoint(memberB.getMemberId(), 100_000L));

    Auction auctionAWins = saveAuction(memberB);
    Auction auctionBWins = saveAuction(memberA);
    long priceAWins = 10_500L;
    long priceBWins = 20_000L;

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    // when
    List<String> results;
    try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
      Future<String> aWinsResult =
          executorService.submit(
              () ->
                  settleAfterSignal(
                      auctionAWins.getAuctionId(),
                      memberA.getMemberId(),
                      memberB.getMemberId(),
                      priceAWins,
                      ready,
                      start));
      Future<String> bWinsResult =
          executorService.submit(
              () ->
                  settleAfterSignal(
                      auctionBWins.getAuctionId(),
                      memberB.getMemberId(),
                      memberA.getMemberId(),
                      priceBWins,
                      ready,
                      start));
      ready.await();
      start.countDown();
      results =
          List.of(
              aWinsResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
              bWinsResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    // then: 제한 시간 내에 둘 다 성공적으로 끝나야 한다(교착상태였다면 timeout 되어 예외가 발생한다)
    assertThat(results).containsOnly("SUCCESS");
    long balanceA =
        pointJpaRepository.findByMemberId(memberA.getMemberId()).orElseThrow().getBalance();
    long balanceB =
        pointJpaRepository.findByMemberId(memberB.getMemberId()).orElseThrow().getBalance();
    assertThat(balanceA).isEqualTo(100_000L - priceAWins + priceBWins);
    assertThat(balanceB).isEqualTo(100_000L + priceAWins - priceBWins);
    assertThat(settlementJpaRepository.findAll())
        .extracting(Settlement::getSettlementType)
        .containsExactlyInAnyOrder(
            SettlementType.WINNER_PAYMENT,
            SettlementType.SELLER_PAYOUT,
            SettlementType.WINNER_PAYMENT,
            SettlementType.SELLER_PAYOUT);
  }

  @Test
  void 같은_정산이벤트가_중복전달되어_동시에_처리되어도_정산과_포인트가_한번만_반영된다() throws Exception {
    // given: SQS 재전달 등으로 같은 경매의 같은 정산 이벤트가 동시에 두 번 처리되는 상황을 흉내낸다.
    Member winner = memberJpaRepository.save(Member.create("winner", "password", "낙찰자"));
    Member seller = memberJpaRepository.save(Member.create("seller", "password", "판매자"));
    pointJpaRepository.save(seedPoint(winner.getMemberId(), 50_000L));
    pointJpaRepository.save(seedPoint(seller.getMemberId(), 5_000L));
    Auction auction = saveAuction(seller);
    long winningPrice = 10_500L;

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    // when
    List<String> results;
    try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
      Future<String> firstDelivery =
          executorService.submit(
              () ->
                  settleAfterSignal(
                      auction.getAuctionId(),
                      winner.getMemberId(),
                      seller.getMemberId(),
                      winningPrice,
                      ready,
                      start));
      Future<String> duplicateDelivery =
          executorService.submit(
              () ->
                  settleAfterSignal(
                      auction.getAuctionId(),
                      winner.getMemberId(),
                      seller.getMemberId(),
                      winningPrice,
                      ready,
                      start));
      ready.await();
      start.countDown();
      results =
          List.of(
              firstDelivery.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
              duplicateDelivery.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    // then: 유니크 제약에 막힌 쪽은 트랜잭션 전체가 롤백되어 실패로 끝난다(그 경합을 "이미 처리됨"으로 해석해 정상 소비하는 것은
    // SettlementEventHandler의 책임이라 여기서는 다루지 않는다). 그래도 정산/포인트는 한 번만 반영돼야 한다.
    assertThat(results).containsExactlyInAnyOrder("SUCCESS", "FAILURE");
    long balanceWinner =
        pointJpaRepository.findByMemberId(winner.getMemberId()).orElseThrow().getBalance();
    long balanceSeller =
        pointJpaRepository.findByMemberId(seller.getMemberId()).orElseThrow().getBalance();
    assertThat(balanceWinner).isEqualTo(50_000L - winningPrice);
    assertThat(balanceSeller).isEqualTo(5_000L + winningPrice);
    assertThat(settlementJpaRepository.findAll())
        .extracting(Settlement::getSettlementType)
        .containsExactlyInAnyOrder(SettlementType.WINNER_PAYMENT, SettlementType.SELLER_PAYOUT);
  }

  private String settleAfterSignal(
      Long auctionId,
      Long winnerMemberId,
      Long sellerMemberId,
      Long winningPrice,
      CountDownLatch ready,
      CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    try {
      settlementService.settleAuction(auctionId, winnerMemberId, sellerMemberId, winningPrice);
      return "SUCCESS";
    } catch (RuntimeException exception) {
      return "FAILURE";
    }
  }

  private Auction saveAuction(Member consignmentSeller) {
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
                .sellerMember(consignmentSeller)
                .status(ConsignmentStatus.IN_AUCTION)
                .build());
    Auction auction =
        Auction.builder()
            .title("테스트 제목")
            .description("테스트 설명")
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now())
            .auctionStatus(AuctionStatus.WON)
            .startingPrice(10_000L)
            .reservePrice(10_000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "legacyUnreservedBid", true);
    return auctionJpaRepository.saveAndFlush(auction);
  }

  private Point seedPoint(Long memberId, long balance) {
    Point point = Point.create(memberId);
    if (balance > 0) {
      point.increaseBalance(balance);
    }
    return point;
  }
}
