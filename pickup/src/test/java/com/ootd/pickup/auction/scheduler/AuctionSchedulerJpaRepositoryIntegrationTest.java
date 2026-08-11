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

  private static final long RESERVE_PRICE = 15000L;

  @Autowired private AuctionSchedulerJpaRepository auctionSchedulerJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Test
  void 시작_시각이_지난_예정_경매만_조회된다() {
    // given
    Auction due = createAuction(AuctionStatus.SCHEDULED, past(1), null);
    Auction notYet = createAuction(AuctionStatus.SCHEDULED, future(1), null);
    Auction alreadyOngoing = createAuction(AuctionStatus.ONGOING, past(60), null);

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
            AuctionStatus.SCHEDULED, Limit.of(100));

    // then
    assertThat(auctionIds)
        .containsExactly(due.getAuctionId())
        .doesNotContain(notYet.getAuctionId(), alreadyOngoing.getAuctionId());
  }

  @Test
  void 종료_시각이_지난_진행_경매만_조회된다() {
    // given
    Auction due = createAuction(AuctionStatus.ONGOING, past(120), past(1));
    Auction notYet = createAuction(AuctionStatus.ONGOING, past(120), future(1));
    Auction alreadyWon = createAuction(AuctionStatus.WON, past(120), past(60));

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
            AuctionStatus.ONGOING, Limit.of(100));

    // then
    assertThat(auctionIds)
        .containsExactly(due.getAuctionId())
        .doesNotContain(notYet.getAuctionId(), alreadyWon.getAuctionId());
  }

  @Test
  void 종료_시각이_없는_경매는_종료_대상에서_제외된다() {
    // given
    Auction noEndedAt = createAuction(AuctionStatus.ONGOING, past(120), null);

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
            AuctionStatus.ONGOING, Limit.of(100));

    // then
    assertThat(auctionIds).doesNotContain(noEndedAt.getAuctionId());
  }

  @Test
  void 조회_건수는_주어진_상한을_넘지_않는다() {
    // given
    createAuction(AuctionStatus.SCHEDULED, past(180), null);
    createAuction(AuctionStatus.SCHEDULED, past(120), null);
    createAuction(AuctionStatus.SCHEDULED, past(60), null);

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
            AuctionStatus.SCHEDULED, Limit.of(2));

    // then
    assertThat(auctionIds).hasSize(2);
  }

  @Test
  void 종료_대상은_가장_오래_밀린_경매부터_조회된다() {
    // given — 등록 순서(식별자)와 마감 순서가 어긋나게 심는다
    Auction recentlyDue = createAuction(AuctionStatus.ONGOING, past(120), past(1));
    Auction longOverdue = createAuction(AuctionStatus.ONGOING, past(120), past(3));
    Auction middle = createAuction(AuctionStatus.ONGOING, past(120), past(2));

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
            AuctionStatus.ONGOING, Limit.of(100));

    // then
    assertThat(auctionIds)
        .containsExactly(
            longOverdue.getAuctionId(), middle.getAuctionId(), recentlyDue.getAuctionId());
  }

  @Test
  void 시작_대상은_시작_시각이_이른_경매부터_조회된다() {
    // given — 등록 순서(식별자)와 시작 순서가 어긋나게 심는다
    Auction lateStarted = createAuction(AuctionStatus.SCHEDULED, past(1), null);
    Auction earlyStarted = createAuction(AuctionStatus.SCHEDULED, past(3), null);
    Auction middle = createAuction(AuctionStatus.SCHEDULED, past(2), null);

    // when
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
            AuctionStatus.SCHEDULED, Limit.of(100));

    // then
    assertThat(auctionIds)
        .containsExactly(
            earlyStarted.getAuctionId(), middle.getAuctionId(), lateStarted.getAuctionId());
  }

  // 조회 쿼리가 기준 시각을 DB 에서 읽으므로 고정 시각을 넣을 수 없다. 현재를 기준으로 상대 시각을 심는다.
  private LocalDateTime past(long minutes) {
    return LocalDateTime.now().minusMinutes(minutes);
  }

  private LocalDateTime future(long minutes) {
    return LocalDateTime.now().plusMinutes(minutes);
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
                .rarity(Rarity.RARE_HOLO)
                .imageUrl("https://example.com/scheduler.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .status(ConsignmentStatus.IN_AUCTION)
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
