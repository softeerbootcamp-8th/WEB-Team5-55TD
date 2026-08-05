package com.ootd.pickup.auction.scheduler;

import static org.assertj.core.api.Assertions.*;

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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionSchedulerJpaRepositoryIntegrationTest {

  private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 5, 12, 0, 0);
  private static final long RESERVE_PRICE = 15000L;

  @Autowired private AuctionSchedulerJpaRepository auctionSchedulerJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Test
  void 시작_시각이_지난_예정_경매만_조회된다() {
    // given
    Auction due = createAuction(AuctionStatus.SCHEDULED, BASE_TIME.minusSeconds(1), null);
    Auction exactlyNow = createAuction(AuctionStatus.SCHEDULED, BASE_TIME, null);
    Auction notYet = createAuction(AuctionStatus.SCHEDULED, BASE_TIME.plusSeconds(1), null);
    Auction alreadyOngoing = createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(1), null);

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqual(
            AuctionStatus.SCHEDULED, BASE_TIME, Limit.of(100));

    // then
    assertThat(auctionIds)
        .containsExactly(due.getAuctionId(), exactlyNow.getAuctionId())
        .doesNotContain(notYet.getAuctionId(), alreadyOngoing.getAuctionId());
  }

  @Test
  void 종료_시각이_지난_진행_경매만_조회된다() {
    // given
    Auction due =
        createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(2), BASE_TIME.minusSeconds(1));
    Auction notYet =
        createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(2), BASE_TIME.plusSeconds(1));
    Auction alreadyWon =
        createAuction(AuctionStatus.WON, BASE_TIME.minusHours(2), BASE_TIME.minusHours(1));

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqual(
            AuctionStatus.ONGOING, BASE_TIME, Limit.of(100));

    // then
    assertThat(auctionIds)
        .containsExactly(due.getAuctionId())
        .doesNotContain(notYet.getAuctionId(), alreadyWon.getAuctionId());
  }

  @Test
  void 종료_시각이_없는_경매는_종료_대상에서_제외된다() {
    // given
    Auction noEndedAt = createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(2), null);

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqual(
            AuctionStatus.ONGOING, BASE_TIME, Limit.of(100));

    // then
    assertThat(auctionIds).doesNotContain(noEndedAt.getAuctionId());
  }

  @Test
  void 조회_건수는_주어진_상한을_넘지_않는다() {
    // given
    createAuction(AuctionStatus.SCHEDULED, BASE_TIME.minusHours(3), null);
    createAuction(AuctionStatus.SCHEDULED, BASE_TIME.minusHours(2), null);
    createAuction(AuctionStatus.SCHEDULED, BASE_TIME.minusHours(1), null);

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqual(
            AuctionStatus.SCHEDULED, BASE_TIME, Limit.of(2));

    // then
    assertThat(auctionIds).hasSize(2);
  }

  @Test
  void 시작_전이는_예정_상태인_경매만_갱신한다() {
    // given
    Auction scheduled = createAuction(AuctionStatus.SCHEDULED, BASE_TIME.minusHours(1), null);
    Auction alreadyOngoing = createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(1), null);

    // when
    int updated =
        auctionSchedulerJpaRepository.updateAuctionStatusToOngoingByIdIn(
            List.of(scheduled.getAuctionId(), alreadyOngoing.getAuctionId()));

    // then
    assertThat(updated).isEqualTo(1);
    assertThat(findStatus(scheduled.getAuctionId())).isEqualTo(AuctionStatus.ONGOING);
  }

  @Test
  void 리저브를_채운_경매는_낙찰로_전이된다() {
    // given
    Auction auction = ongoingWithWinningPrice(RESERVE_PRICE);

    // when
    int won =
        auctionSchedulerJpaRepository.updateAuctionStatusToWonByIdIn(
            List.of(auction.getAuctionId()));

    // then
    assertThat(won).isEqualTo(1);
    assertThat(findStatus(auction.getAuctionId())).isEqualTo(AuctionStatus.WON);
  }

  @Test
  void 리저브에_미달한_경매는_유찰로_전이된다() {
    // given
    Auction auction = ongoingWithWinningPrice(RESERVE_PRICE - 1);

    // when
    int passed =
        auctionSchedulerJpaRepository.updateAuctionStatusToPassedByIdIn(
            List.of(auction.getAuctionId()));

    // then
    assertThat(passed).isEqualTo(1);
    assertThat(findStatus(auction.getAuctionId())).isEqualTo(AuctionStatus.PASSED);
  }

  @Test
  void 입찰이_없는_경매는_유찰로_전이된다() {
    // given
    Auction noBid = createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(2), BASE_TIME);

    // when
    int passed =
        auctionSchedulerJpaRepository.updateAuctionStatusToPassedByIdIn(
            List.of(noBid.getAuctionId()));

    // then
    assertThat(passed).isEqualTo(1);
    assertThat(findStatus(noBid.getAuctionId())).isEqualTo(AuctionStatus.PASSED);
  }

  @Test
  void 낙찰_조건과_유찰_조건은_같은_경매를_함께_가져가지_않는다() {
    // given
    Auction reserveMet = ongoingWithWinningPrice(RESERVE_PRICE);
    Auction reserveNotMet = ongoingWithWinningPrice(RESERVE_PRICE - 1);
    Auction noBid = createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(2), BASE_TIME);
    List<Long> auctionIds =
        List.of(reserveMet.getAuctionId(), reserveNotMet.getAuctionId(), noBid.getAuctionId());

    // when
    int won = auctionSchedulerJpaRepository.updateAuctionStatusToWonByIdIn(auctionIds);
    int passed = auctionSchedulerJpaRepository.updateAuctionStatusToPassedByIdIn(auctionIds);

    // then
    assertThat(won).isEqualTo(1);
    assertThat(passed).isEqualTo(2);
    assertThat(won + passed).isEqualTo(auctionIds.size());
  }

  @Test
  void 종료_전이는_낙찰가를_덮지_않는다() {
    // given
    Auction auction = ongoingWithWinningPrice(99000L);

    // when
    auctionSchedulerJpaRepository.updateAuctionStatusToWonByIdIn(List.of(auction.getAuctionId()));

    // then
    Auction reloaded = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(reloaded.getAuctionStatus()).isEqualTo(AuctionStatus.WON);
    assertThat(reloaded.getWinningBidId()).isEqualTo(777L);
    assertThat(reloaded.getWinningPrice()).isEqualTo(99000L);
  }

  private Auction ongoingWithWinningPrice(long winningPrice) {
    Auction auction = createAuction(AuctionStatus.ONGOING, BASE_TIME.minusHours(2), BASE_TIME);
    auction.updateWinningBid(777L, winningPrice);
    return auctionJpaRepository.saveAndFlush(auction);
  }

  private AuctionStatus findStatus(Long auctionId) {
    return auctionJpaRepository.findById(auctionId).orElseThrow().getAuctionStatus();
  }

  private Auction createAuction(
      AuctionStatus auctionStatus, LocalDateTime startedAt, LocalDateTime endedAt) {
    String unique = "scheduler-repo-" + System.nanoTime();
    Member sellerMember =
        memberJpaRepository.save(Member.create(unique, "password", unique + "-nickname"));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName(unique)
                .cardNumber(unique)
                .setName("스케줄러 테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://example.com/scheduler.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .status(ConsignmentStatus.AUCTION_SCHEDULED)
                .build());
    return auctionJpaRepository.saveAndFlush(
        Auction.builder()
            .consignment(consignment)
            .startedAt(startedAt)
            .endedAt(endedAt)
            .auctionStatus(auctionStatus)
            .startingPrice(10000L)
            .reservePrice(RESERVE_PRICE)
            .bidIncrement(500L)
            .build());
  }
}
