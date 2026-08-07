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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionSchedulerIntegrationTest {

  private static final long RESERVE_PRICE = 15000L;

  @Autowired private AuctionScheduler auctionScheduler;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Test
  void 시작_시각이_지난_예정_경매는_진행중으로_전이된다() {
    // given
    Auction due = createAuction(AuctionStatus.SCHEDULED, past(1), future(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findStatus(due)).isEqualTo(AuctionStatus.ONGOING);
  }

  @Test
  void 시작_시각이_남은_예정_경매는_전이되지_않는다() {
    // given
    Auction notYet = createAuction(AuctionStatus.SCHEDULED, future(1), future(2));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findStatus(notYet)).isEqualTo(AuctionStatus.SCHEDULED);
  }

  @Test
  void 종료_시각이_지나고_리저브를_채운_경매는_낙찰된다() {
    // given
    Auction due = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    due.updateWinningBid(777L, RESERVE_PRICE);
    auctionJpaRepository.saveAndFlush(due);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findStatus(due)).isEqualTo(AuctionStatus.WON);
  }

  @Test
  void 종료_시각이_지나고_리저브에_미달한_경매는_유찰된다() {
    // given
    Auction due = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    due.updateWinningBid(777L, RESERVE_PRICE - 1);
    auctionJpaRepository.saveAndFlush(due);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findStatus(due)).isEqualTo(AuctionStatus.PASSED);
  }

  @Test
  void 입찰이_없이_종료된_경매는_유찰된다() {
    // given
    Auction noBid = createAuction(AuctionStatus.ONGOING, past(2), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findStatus(noBid)).isEqualTo(AuctionStatus.PASSED);
  }

  @Test
  void 시작과_종료_시각이_모두_지난_예정_경매는_한_주기에_판정까지_전이된다() {
    // given
    Auction longOverdue = createAuction(AuctionStatus.SCHEDULED, past(3), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then — 입찰이 없었으므로 유찰까지 간다
    assertThat(findStatus(longOverdue)).isEqualTo(AuctionStatus.PASSED);
  }

  @Test
  void 전이는_낙찰가를_덮지_않는다() {
    // given
    Auction auction = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    auction.updateWinningBid(777L, 99000L);
    auctionJpaRepository.saveAndFlush(auction);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    Auction reloaded = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(reloaded.getAuctionStatus()).isEqualTo(AuctionStatus.WON);
    assertThat(reloaded.getWinningBidId()).isEqualTo(777L);
    assertThat(reloaded.getWinningPrice()).isEqualTo(99000L);
  }

  @Test
  void 이미_판정된_경매는_다시_전이되지_않는다() {
    // given
    Auction alreadyPassed = createAuction(AuctionStatus.PASSED, past(3), past(2));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findStatus(alreadyPassed)).isEqualTo(AuctionStatus.PASSED);
  }

  private LocalDateTime past(int hours) {
    return LocalDateTime.now().minusHours(hours);
  }

  private LocalDateTime future(int hours) {
    return LocalDateTime.now().plusHours(hours);
  }

  private AuctionStatus findStatus(Auction auction) {
    return auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow().getAuctionStatus();
  }

  private Auction createAuction(
      AuctionStatus auctionStatus, LocalDateTime startedAt, LocalDateTime endedAt) {
    String unique = "auction-scheduler-" + System.nanoTime();
    Member sellerMember =
        memberJpaRepository.save(Member.create(unique, "password", unique + "-nickname"));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName(unique)
                .cardNumber(unique)
                .setName("스케줄러 전이 테스트 세트")
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
