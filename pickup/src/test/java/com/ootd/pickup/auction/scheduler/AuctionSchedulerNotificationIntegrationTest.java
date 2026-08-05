package com.ootd.pickup.auction.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionClosedNotificationEvent;
import com.ootd.pickup.auction.event.AuctionStartedNotificationEvent;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.event.outbox.OutboxEventJpaRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 경매 시작 알림이 <b>커밋 이후에</b> 발행되는지 확인한다.
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다. 테스트가 트랜잭션을 열고 롤백하면 스케줄러의 트랜잭션이 그 안에 합류해 커밋이 일어나지 않고,
 * {@code afterCommit} 훅이 아예 실행되지 않는다. 검증 대상이 그 훅이므로 실제로 커밋시켜야 한다.
 *
 * <p>커밋되는 만큼 뒷정리를 직접 한다. H2가 JVM 안에서 공유되므로 남겨두면 다른 테스트가 이 경매를 집어간다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuctionSchedulerNotificationIntegrationTest {

  private static final long RESERVE_PRICE = 15000L;

  @Autowired private AuctionScheduler auctionScheduler;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private OutboxEventJpaRepository outboxEventJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private EventPublisher eventPublisher;

  private final List<Long> createdAuctionIds = new ArrayList<>();
  private final List<Long> createdConsignmentIds = new ArrayList<>();
  private final List<Long> createdCardIds = new ArrayList<>();
  private final List<Long> createdMemberIds = new ArrayList<>();

  @AfterEach
  void 커밋된_데이터를_지운다() {
    transactionTemplate.executeWithoutResult(
        status -> {
          outboxEventJpaRepository.deleteAll();
          createdAuctionIds.forEach(auctionJpaRepository::deleteById);
          createdConsignmentIds.forEach(consignmentJpaRepository::deleteById);
          createdCardIds.forEach(cardJpaRepository::deleteById);
          createdMemberIds.forEach(memberJpaRepository::deleteById);
        });
    createdAuctionIds.clear();
    createdConsignmentIds.clear();
    createdCardIds.clear();
    createdMemberIds.clear();
  }

  @Test
  void 시작_전이가_일어나면_시작_알림이_발행된다() {
    // given
    Auction due = createAuction(AuctionStatus.SCHEDULED, past(1), future(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    List<NotificationEvent> published = publishedFor(due.getAuctionId());
    assertThat(published).hasSize(1);
    assertThat(published.getFirst()).isInstanceOf(AuctionStartedNotificationEvent.class);
  }

  @Test
  void 알림은_전이가_커밋된_뒤에_발행된다() {
    // given — 발행 시점에 별도 트랜잭션으로 읽어 이미 커밋됐는지 확인한다.
    // 커밋 전에 발행되면 새 트랜잭션은 아직 SCHEDULED 를 본다.
    Auction due = createAuction(AuctionStatus.SCHEDULED, past(1), future(1));
    List<AuctionStatus> statusSeenWhenPublished = new ArrayList<>();
    willAnswer(
            invocation -> {
              NotificationEvent event = invocation.getArgument(0);
              if (due.getAuctionId().equals(event.aggregateId())) {
                statusSeenWhenPublished.add(readCommittedStatus(due.getAuctionId()));
              }
              return null;
            })
        .given(eventPublisher)
        .publish(any());

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(statusSeenWhenPublished).containsExactly(AuctionStatus.ONGOING);
  }

  @Test
  void 시작_전이가_없으면_알림을_발행하지_않는다() {
    // given
    Auction notYet = createAuction(AuctionStatus.SCHEDULED, future(1), future(2));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(publishedFor(notYet.getAuctionId())).isEmpty();
  }

  @Test
  void 종료_전이가_일어나면_종료_알림이_발행된다() {
    // given — 같은 사건이 Outbox(정산)와 Redis(화면 갱신) 양쪽으로 나간다
    Auction closing = createAuction(AuctionStatus.ONGOING, past(2), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    List<NotificationEvent> published = publishedFor(closing.getAuctionId());
    assertThat(published).hasSize(1);
    assertThat(published.getFirst()).isInstanceOf(AuctionClosedNotificationEvent.class);
    assertThat(((AuctionClosedNotificationEvent) published.getFirst()).auctionStatus())
        .isEqualTo(AuctionStatus.PASSED);
  }

  @Test
  void 종료_알림에는_리저브가_담기지_않는다() {
    // given — 비공개 값이라 구독한 모든 클라이언트로 흘러가면 안 된다
    Auction closing = createAuction(AuctionStatus.ONGOING, past(2), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(AuctionClosedNotificationEvent.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("reservePrice");
    assertThat(publishedFor(closing.getAuctionId())).hasSize(1);
  }

  @Test
  void 시작과_종료가_한_주기에_일어나면_두_알림이_모두_발행된다() {
    // given — 장기간 중단 후 재개하면 한 주기에 SCHEDULED -> ONGOING -> PASSED 까지 간다
    Auction overdue = createAuction(AuctionStatus.SCHEDULED, past(3), past(1));

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(publishedFor(overdue.getAuctionId()))
        .hasSize(2)
        .hasAtLeastOneElementOfType(AuctionStartedNotificationEvent.class)
        .hasAtLeastOneElementOfType(AuctionClosedNotificationEvent.class);
  }

  @Test
  void 알림_발행이_실패해도_전이는_유지된다() {
    // given — 알림은 유실이 허용된다. 발행 실패가 이미 커밋된 전이를 되돌리면 안 된다
    Auction due = createAuction(AuctionStatus.SCHEDULED, past(1), future(1));
    willThrow(new IllegalStateException("Redis 장애")).given(eventPublisher).publish(any());

    // when
    auctionScheduler.transitionDueAuctions();

    // then
    assertThat(readCommittedStatus(due.getAuctionId())).isEqualTo(AuctionStatus.ONGOING);
  }

  /**
   * 주어진 경매에 대해 발행된 알림만 고른다.
   *
   * <p>H2 가 JVM 안에서 공유되므로 전역 발행 횟수를 단언하면 실행 순서에 따라 결과가 달라진다.
   */
  private List<NotificationEvent> publishedFor(Long auctionId) {
    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    then(eventPublisher).should(atLeast(0)).publish(captor.capture());
    return captor.getAllValues().stream()
        .filter(event -> auctionId.equals(event.aggregateId()))
        .toList();
  }

  private AuctionStatus readCommittedStatus(Long auctionId) {
    return transactionTemplate.execute(
        status -> auctionJpaRepository.findById(auctionId).orElseThrow().getAuctionStatus());
  }

  private LocalDateTime past(int hours) {
    return LocalDateTime.now().minusHours(hours);
  }

  private LocalDateTime future(int hours) {
    return LocalDateTime.now().plusHours(hours);
  }

  private Auction createAuction(
      AuctionStatus auctionStatus, LocalDateTime startedAt, LocalDateTime endedAt) {
    return transactionTemplate.execute(
        status -> {
          String unique = "notify-" + System.nanoTime();
          Member sellerMember =
              memberJpaRepository.save(Member.create(unique, "password", unique + "-nickname"));
          Card card =
              cardJpaRepository.save(
                  Card.builder()
                      .cardName(unique)
                      .cardNumber(unique)
                      .setName("알림 테스트 세트")
                      .language(Language.KOREAN)
                      .rarity(Rarity.MINT)
                      .imageUrl("https://example.com/notify.png")
                      .build());
          Consignment consignment =
              consignmentJpaRepository.save(
                  Consignment.builder()
                      .card(card)
                      .sellerMember(sellerMember)
                      .status(ConsignmentStatus.AUCTION_SCHEDULED)
                      .build());
          Auction auction =
              auctionJpaRepository.save(
                  Auction.builder()
                      .consignment(consignment)
                      .startedAt(startedAt)
                      .endedAt(endedAt)
                      .auctionStatus(auctionStatus)
                      .startingPrice(10000L)
                      .reservePrice(RESERVE_PRICE)
                      .bidIncrement(500L)
                      .build());

          createdMemberIds.add(sellerMember.getMemberId());
          createdCardIds.add(card.getCardId());
          createdConsignmentIds.add(consignment.getConsignmentId());
          createdAuctionIds.add(auction.getAuctionId());
          return auction;
        });
  }
}
