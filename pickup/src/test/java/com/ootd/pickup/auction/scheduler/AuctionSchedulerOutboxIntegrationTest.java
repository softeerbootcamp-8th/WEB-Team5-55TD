package com.ootd.pickup.auction.scheduler;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.repository.BidJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.outbox.OutboxEventEntity;
import com.ootd.pickup.global.event.outbox.OutboxEventJpaRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionSchedulerOutboxIntegrationTest {

  private static final long RESERVE_PRICE = 15000L;

  @Autowired private AuctionScheduler auctionScheduler;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private OutboxEventJpaRepository outboxEventJpaRepository;

  @Autowired private BidJpaRepository bidJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private ObjectMapper objectMapper;

  private final List<Long> createdAuctionIds = new ArrayList<>();

  @Test
  void 낙찰로_종료되면_판매자와_낙찰자_식별자가_담긴_이벤트가_적재된다() {
    // given
    Member seller = createMember("seller");
    Member bidder = createMember("bidder");
    Auction auction = createAuction(seller, AuctionStatus.ONGOING, past(2), past(1));
    Bid winningBid = bidJpaRepository.saveAndFlush(Bid.create(auction, bidder, RESERVE_PRICE));
    auction.updateWinningBid(winningBid.getBidId(), RESERVE_PRICE);
    auctionJpaRepository.saveAndFlush(auction);

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    AuctionEndedMessageQueueEvent event = singleAppendedEvent();
    assertThat(event.auctionId()).isEqualTo(auction.getAuctionId());
    assertThat(event.auctionStatus()).isEqualTo(AuctionStatus.WON);
    assertThat(event.sellerMemberId()).isEqualTo(seller.getMemberId());
    assertThat(event.winnerMemberId()).isEqualTo(bidder.getMemberId());
    assertThat(event.winningBidId()).isEqualTo(winningBid.getBidId());
    assertThat(event.winningPrice()).isEqualTo(RESERVE_PRICE);
    assertThat(event.reservePrice()).isEqualTo(RESERVE_PRICE);
  }

  @Test
  void 유찰로_종료되면_낙찰자_식별자가_없는_이벤트가_적재된다() {
    // given
    Member seller = createMember("seller-passed");
    Auction auction = createAuction(seller, AuctionStatus.ONGOING, past(2), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    AuctionEndedMessageQueueEvent event = singleAppendedEvent();
    assertThat(event.auctionStatus()).isEqualTo(AuctionStatus.PASSED);
    assertThat(event.sellerMemberId()).isEqualTo(seller.getMemberId());
    assertThat(event.winnerMemberId()).isNull();
    assertThat(event.winningBidId()).isNull();
    assertThat(event.auctionId()).isEqualTo(auction.getAuctionId());
  }

  @Test
  void 적재된_행의_컬럼은_이벤트_계약을_따른다() {
    // given
    createAuction(createMember("seller-columns"), AuctionStatus.ONGOING, past(2), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    OutboxEventEntity appended = singleAppendedRow();
    assertThat(appended.getEventType()).isEqualTo(EventType.AUCTION_ENDED);
    assertThat(appended.getAggregateType()).isEqualTo(AggregateType.AUCTION);
    assertThat(appended.isPublished()).isFalse();
    assertThat(appended.getId()).hasSize(36);
  }

  @Test
  void 적재된_행의_식별자는_payload_의_이벤트_식별자와_같다() {
    // given
    createAuction(createMember("seller-id"), AuctionStatus.ONGOING, past(2), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then — 소비자가 로그에 남긴 eventId 로 Outbox 행을 PK 로 찾을 수 있어야 한다
    OutboxEventEntity appended = singleAppendedRow();
    assertThat(appended.getId()).isEqualTo(singleAppendedEvent().eventId());
  }

  @Test
  void 시작만_전이된_경매는_적재하지_않는다() {
    // given — 경매 시작은 알림 계열이라 Outbox 를 거치지 않는다
    createAuction(createMember("seller-started"), AuctionStatus.SCHEDULED, past(1), future(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(appendedRows()).isEmpty();
  }

  @Test
  void 전이_대상이_없으면_적재하지_않는다() {
    // given
    createAuction(createMember("seller-none"), AuctionStatus.SCHEDULED, future(1), future(2));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(appendedRows()).isEmpty();
  }

  @Test
  void 낙찰과_유찰이_섞여도_각_경매마다_한_건씩_적재된다() {
    // given
    Member seller = createMember("seller-mixed");
    Member bidder = createMember("bidder-mixed");
    Auction willWin = createAuction(seller, AuctionStatus.ONGOING, past(2), past(1));
    Bid winningBid = bidJpaRepository.saveAndFlush(Bid.create(willWin, bidder, RESERVE_PRICE));
    willWin.updateWinningBid(winningBid.getBidId(), RESERVE_PRICE);
    auctionJpaRepository.saveAndFlush(willWin);
    createAuction(seller, AuctionStatus.ONGOING, past(2), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    List<AuctionStatus> statuses =
        appendedRows().stream()
            .map(this::deserialize)
            .map(AuctionEndedMessageQueueEvent::auctionStatus)
            .toList();
    assertThat(statuses).containsExactlyInAnyOrder(AuctionStatus.WON, AuctionStatus.PASSED);
  }

  private AuctionEndedMessageQueueEvent singleAppendedEvent() {
    return deserialize(singleAppendedRow());
  }

  /**
   * 이 테스트가 만든 경매의 적재분만 고른다.
   *
   * <p>H2 가 JVM 안에서 공유되고 다른 테스트가 경매를 커밋하므로, 전역 건수를 단언하면 실행 순서에 따라 결과가 달라진다.
   *
   * <p>애그리거트는 종류와 식별자가 짝을 이뤄야 특정된다. 식별자만 보면 다른 종류의 애그리거트가 같은 숫자를 가질 때 이 테스트의 행으로 섞여 들어온다.
   */
  private List<OutboxEventEntity> appendedRows() {
    return outboxEventJpaRepository.findAll().stream()
        .filter(row -> row.getAggregateType() == AggregateType.AUCTION)
        .filter(row -> createdAuctionIds.contains(row.getAggregateId()))
        .toList();
  }

  private OutboxEventEntity singleAppendedRow() {
    List<OutboxEventEntity> appended = appendedRows();
    assertThat(appended).hasSize(1);
    return appended.getFirst();
  }

  private AuctionEndedMessageQueueEvent deserialize(OutboxEventEntity row) {
    return objectMapper.readValue(row.getPayload(), AuctionEndedMessageQueueEvent.class);
  }

  private LocalDateTime past(int hours) {
    return LocalDateTime.now().minusHours(hours);
  }

  private LocalDateTime future(int hours) {
    return LocalDateTime.now().plusHours(hours);
  }

  private Member createMember(String prefix) {
    String unique = prefix + "-" + System.nanoTime();
    return memberJpaRepository.saveAndFlush(
        Member.create(unique, "password", unique + "-nickname"));
  }

  private Auction createAuction(
      Member sellerMember,
      AuctionStatus auctionStatus,
      LocalDateTime startedAt,
      LocalDateTime endedAt) {
    String unique = "outbox-" + System.nanoTime();
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName(unique)
                .cardNumber(unique)
                .setName("Outbox 적재 테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://example.com/outbox.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .status(ConsignmentStatus.AUCTION_SCHEDULED)
                .build());
    Auction auction =
        auctionJpaRepository.saveAndFlush(
            Auction.builder()
                .consignment(consignment)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .auctionStatus(auctionStatus)
                .startingPrice(10000L)
                .reservePrice(RESERVE_PRICE)
                .bidIncrement(500L)
                .build());

    createdAuctionIds.add(auction.getAuctionId());
    return auction;
  }
}
