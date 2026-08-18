package com.ootd.pickup.auction.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

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
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시작 전이와 종료 전이가 <b>서로 다른 트랜잭션</b>에서 도는지 확인한다.
 *
 * <p>종료 단계를 실패시켰을 때 시작 전이가 살아남는지를 본다. 둘이 한 트랜잭션이면 시작 전이도 함께 롤백되므로, 이 클래스가 경계를 지키는 유일한 테스트다. 다른 통합
 * 테스트는 클래스에 {@code @Transactional}이 붙어 두 트랜잭션이 테스트 트랜잭션에 합류하므로 경계를 볼 수 없다.
 *
 * <p>같은 이유로 여기에는 {@code @Transactional}을 붙이지 않는다. 커밋이 실제로 일어나야 검증이 성립한다. 커밋되는 만큼 뒷정리를 직접 한다.
 *
 * <p>{@link EventProducer}를 대역으로 바꿔 종료 단계를 실패시킨다. Outbox 적재가 종료 트랜잭션 안에서 일어나는 마지막 쓰기라, 여기서 터뜨리면 상태
 * 전이까지 되돌아가는지 볼 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuctionSchedulerTransactionBoundaryTest {

  private static final long RESERVE_PRICE = 15000L;

  @Autowired private AuctionScheduler auctionScheduler;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private EventProducer eventProducer;

  @MockitoBean private EventPublisher eventPublisher;

  private final List<Long> createdAuctionIds = new ArrayList<>();
  private final List<Long> createdConsignmentIds = new ArrayList<>();
  private final List<Long> createdCardIds = new ArrayList<>();
  private final List<Long> createdMemberIds = new ArrayList<>();

  @AfterEach
  void 커밋된_데이터를_지운다() {
    transactionTemplate.executeWithoutResult(
        status -> {
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
  void 종료_전이가_실패해도_시작_전이는_커밋된다() {
    // given
    Auction starting = createAuction(AuctionStatus.SCHEDULED, past(1), future(1));
    createAuction(AuctionStatus.ONGOING, past(2), past(1));
    willThrow(new IllegalStateException("Outbox 장애")).given(eventProducer).produce(any());

    // when
    assertThatThrownBy(() -> auctionScheduler.transitionDueAuctions())
        .isInstanceOf(IllegalStateException.class);

    // then
    assertThat(readCommittedStatus(starting.getAuctionId())).isEqualTo(AuctionStatus.ONGOING);
  }

  @Test
  void 종료_전이가_실패하면_종료_대상은_진행중으로_남는다() {
    // given
    Auction closing = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    willThrow(new IllegalStateException("Outbox 장애")).given(eventProducer).produce(any());

    // when
    assertThatThrownBy(() -> auctionScheduler.transitionDueAuctions())
        .isInstanceOf(IllegalStateException.class);

    // then
    assertThat(readCommittedStatus(closing.getAuctionId())).isEqualTo(AuctionStatus.ONGOING);
  }

  @Test
  void 종료_전이가_실패해도_다음_주기가_다시_종료시킨다() {
    // given — 전이 대상을 처리 이력이 아니라 현재 상태로 조회하므로 실패한 쪽만 다시 집어간다
    Auction closing = createAuction(AuctionStatus.ONGOING, past(2), past(1));
    willThrow(new IllegalStateException("Outbox 장애")).given(eventProducer).produce(any());
    assertThatThrownBy(() -> auctionScheduler.transitionDueAuctions())
        .isInstanceOf(IllegalStateException.class);

    // when — 장애가 걷힌 다음 주기
    willDoNothing().given(eventProducer).produce(any());
    auctionScheduler.transitionDueAuctions();

    // then — 입찰이 없었으므로 유찰된다
    assertThat(readCommittedStatus(closing.getAuctionId())).isEqualTo(AuctionStatus.PASSED);
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
          String unique = "boundary-" + System.nanoTime();
          Member sellerMember =
              memberJpaRepository.save(Member.create(unique, "password", unique + "-nickname"));
          Card card =
              cardJpaRepository.save(
                  Card.builder()
                      .cardName(unique)
                      .cardNumber(unique)
                      .setName("트랜잭션 경계 테스트 세트")
                      .language(Language.KOREAN)
                      .rarity(Rarity.RARE_HOLO)
                      .imageUrl("https://example.com/boundary.png")
                      .build());
          Consignment consignment =
              consignmentJpaRepository.save(
                  Consignment.builder()
                      .card(card)
                      .sellerMember(sellerMember)
                      .status(ConsignmentStatus.IN_AUCTION)
                      .build());
          Auction auction =
              auctionJpaRepository.save(
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

          createdMemberIds.add(sellerMember.getMemberId());
          createdCardIds.add(card.getCardId());
          createdConsignmentIds.add(consignment.getConsignmentId());
          createdAuctionIds.add(auction.getAuctionId());
          return auction;
        });
  }
}
