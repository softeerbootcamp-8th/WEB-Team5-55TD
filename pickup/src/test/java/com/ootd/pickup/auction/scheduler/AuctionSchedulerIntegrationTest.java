package com.ootd.pickup.auction.scheduler;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.repository.BidJpaRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionSchedulerIntegrationTest {

  private static final long RESERVE_PRICE = 15000L;

  @Autowired private AuctionScheduler auctionScheduler;

  @Autowired private AuctionRepository auctionRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private BidJpaRepository bidJpaRepository;

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
  void 경매가_시작되어도_위탁_상품_상태는_그대로_유지된다() {
    // given — 위탁 상품은 경매 신청 시점에 이미 IN_AUCTION으로 전이되어 있다
    Auction due = createAuction(AuctionStatus.SCHEDULED, past(1), future(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findConsignmentStatus(due)).isEqualTo(ConsignmentStatus.IN_AUCTION);
  }

  @Test
  void 경매가_낙찰되면_위탁_상품도_판매_완료로_전이된다() {
    // given
    Auction due = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    due.updateWinningBid(777L, RESERVE_PRICE);
    auctionJpaRepository.saveAndFlush(due);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findConsignmentStatus(due)).isEqualTo(ConsignmentStatus.SOLD);
  }

  @Test
  void 경매가_유찰되면_위탁_상품도_재등록_가능_상태로_전이된다() {
    // given
    Auction due = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    due.updateWinningBid(777L, RESERVE_PRICE - 1);
    auctionJpaRepository.saveAndFlush(due);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findConsignmentStatus(due)).isEqualTo(ConsignmentStatus.REGISTERABLE);
  }

  @Test
  void 경매가_낙찰되면_낙찰_입찰의_상태도_WON으로_전이된다() {
    // given
    Auction due = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    Bid winningBid = saveBid(due, RESERVE_PRICE);
    due.updateWinningBid(winningBid.getBidId(), RESERVE_PRICE);
    auctionJpaRepository.saveAndFlush(due);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findBidStatus(winningBid)).isEqualTo(BidStatus.WON);
  }

  @Test
  void 경매가_유찰되면_입찰의_상태는_바뀌지_않는다() {
    // given
    Auction due = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    Bid outbidBid = saveBid(due, RESERVE_PRICE - 1);
    due.updateWinningBid(outbidBid.getBidId(), RESERVE_PRICE - 1);
    auctionJpaRepository.saveAndFlush(due);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(findBidStatus(outbidBid)).isEqualTo(BidStatus.HIGHEST);
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

  @Test
  void 후보로_뽑힌_뒤_마감연장으로_종료시각이_미래가_된_경매는_낙찰_전이되지_않는다() {
    // given — 스케줄러가 후보를 뽑은 시점 이후, 마감 임박 입찰로 종료 시각이 미래로 연장된 상황을 재현한다.
    Auction extended = createAuction(AuctionStatus.ONGOING, past(2), future(1));
    extended.updateWinningBid(777L, RESERVE_PRICE);
    auctionJpaRepository.saveAndFlush(extended);

    // when — 이미 지난 종료 시각으로 후보 목록에 담겼다고 가정하고, 전이 쿼리를 직접 호출한다.
    int updated =
        auctionRepository.updateAuctionStatusToWonByIdIn(List.of(extended.getAuctionId()));

    // then
    assertThat(updated).isZero();
    assertThat(findStatus(extended)).isEqualTo(AuctionStatus.ONGOING);
  }

  @Test
  void 후보로_뽑힌_뒤_마감연장으로_종료시각이_미래가_된_경매는_유찰_전이되지_않는다() {
    // given
    Auction extended = createAuction(AuctionStatus.ONGOING, past(2), future(1));
    extended.updateWinningBid(777L, RESERVE_PRICE - 1);
    auctionJpaRepository.saveAndFlush(extended);

    // when
    int updated =
        auctionRepository.updateAuctionStatusToPassedByIdIn(List.of(extended.getAuctionId()));

    // then
    assertThat(updated).isZero();
    assertThat(findStatus(extended)).isEqualTo(AuctionStatus.ONGOING);
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

  private ConsignmentStatus findConsignmentStatus(Auction auction) {
    Long consignmentId =
        auctionJpaRepository
            .findById(auction.getAuctionId())
            .orElseThrow()
            .getConsignment()
            .getConsignmentId();
    return consignmentJpaRepository.findById(consignmentId).orElseThrow().getStatus();
  }

  private BidStatus findBidStatus(Bid bid) {
    return bidJpaRepository.findById(bid.getBidId()).orElseThrow().getBidStatus();
  }

  private Bid saveBid(Auction auction, long bidPrice) {
    String unique = "auction-scheduler-bidder-" + System.nanoTime();
    Member bidder =
        memberJpaRepository.save(Member.create(unique, "password", unique + "-nickname"));
    return bidJpaRepository.save(Bid.create(auction, bidder, bidPrice));
  }

  /** 위탁 상품 상태는 항상 경매 상태와 짝을 이룬다. 테스트가 만드는 초기 상태도 이 불변조건을 지켜야 한다. */
  private ConsignmentStatus matchingConsignmentStatus(AuctionStatus auctionStatus) {
    return switch (auctionStatus) {
      case SCHEDULED -> ConsignmentStatus.IN_AUCTION;
      case ONGOING -> ConsignmentStatus.IN_AUCTION;
      case WON -> ConsignmentStatus.SOLD;
      case PASSED -> ConsignmentStatus.REGISTERABLE;
    };
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
                .rarity(Rarity.RARE_HOLO)
                .imageUrl("https://example.com/scheduler.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .status(matchingConsignmentStatus(auctionStatus))
                .build());
    return auctionJpaRepository.saveAndFlush(
        Auction.builder()
            .title("테스트 제목")
            .description("테스트 설명")
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
