package com.ootd.pickup.settlement.service;

import static com.ootd.pickup.point.domain.PointReservationStatus.ACTIVE;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.service.AuctionManageService;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointReservation;
import com.ootd.pickup.point.domain.PointTransaction;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.point.repository.PointReservationRepository;
import com.ootd.pickup.point.repository.PointTransactionRepository;
import com.ootd.pickup.point.service.PointLockService;
import com.ootd.pickup.settlement.domain.Settlement;
import com.ootd.pickup.settlement.domain.SettlementType;
import com.ootd.pickup.settlement.repository.SettlementRepository;
import java.util.Arrays;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 마감 정산 서비스.
 *
 * <p>이벤트 수신은 {@code settlement.handler.SettlementEventHandler}가 담당하고, 이 클래스는 순수 비즈니스 로직만 다룬다({@code
 * DomainEvent}/{@code EventHandler}를 모른다). 호출하는 쪽이 SQS 컨슈머든 나중에 생길 수동 재정산 API든 상관없이 같은 메서드를 쓸 수 있다.
 *
 * <p>같은 사건이 재전달될 수 있으므로, 낙찰자/판매자 정산을 각각 독립적으로 멱등하게 처리한다({@code settlement}의 (auctionId, memberId,
 * settlementType) 유니크 제약). 이미 처리된 쪽은 {@code Settlement} 저장과 포인트 갱신을 함께 건너뛴다.
 *
 * <p>인스턴스가 여러 대면 같은 이벤트가 서로 다른 인스턴스에서 동시에 처리될 수 있다. {@code existsBy} 사전 체크만으로는 두 트랜잭션이 동시에 "아직 처리 안
 * 됨"을 보고 둘 다 insert를 시도할 수 있는데, 이때의 실제 안전장치는 {@code settlement} 테이블의 유니크 제약 그 자체다. 정산을 {@code
 * Auction}/{@code Consignment} 같은 다른 도메인의 행을 잠그는 방식으로 직렬화하지 않는 것은 의도적이다 — 정산은 경매가 끝난 뒤 경매·입찰과 완전히
 * 분리된 별도 흐름으로 처리되어야 하고, 경매 쪽 락(예: {@code BidService#placeBid}가 잡는 {@code Auction} 행 락)에 얹혀서 지연되거나
 * 경매 도메인의 락 정책에 묶이면 안 된다.
 *
 * <p>뒤늦게 커밋을 시도하는 트랜잭션은 유니크 제약 위반({@link DataIntegrityViolationException})을 받는다. Hibernate는 {@code
 * GenerationType.IDENTITY}라 insert를 호출 즉시 flush하고, 그 순간 제약을 위반하면 현재 트랜잭션은 무슨 수를 써도 커밋할 수 없는 상태로
 * 확정된다(JPA 스펙상 flush 실패는 트랜잭션을 rollback-only로 만든다 — 이 메서드 안에서 예외를 잡아 계속 진행해도 결국 커밋 시점에 {@code
 * UnexpectedRollbackException}으로 터진다). 그래서 여기서는 예외를 삼키지 않고 그대로 던져 트랜잭션 전체를 롤백시킨다. 이는 실패가 아니라 다른
 * 트랜잭션이 이미 같은 정산을 끝냈다는 신호이므로, 트랜잭션 경계 밖인 {@code SettlementEventHandler}가 이 예외를 "이미 처리됨"으로 해석해 메시지를
 * 정상 소비 처리한다.
 *
 * <p>포인트 잔액 갱신은 {@link PointLockService}를 통해 행 락을 잡는다. 서로 다른 경매의 정산이 동시에 처리되며 같은 회원의 포인트를 건드릴 수 있어,
 * 락 없이는 동시 갱신 하나가 유실될 수 있다(lost update). 또한 두 회원의 락을 잡는 순서가 경매마다 "낙찰자 먼저"로 고정돼 있으면, 경매 A는 회원1→회원2,
 * 경매 B는 회원2→회원1 순으로 잠그려 할 때 교착상태가 날 수 있으므로 {@code PointLockService}가 항상 memberId 오름차순으로 잠근다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SettlementService {

  private final AuctionManageService auctionManageService;
  private final MemberManageService memberManageService;
  private final PointRepository pointRepository;
  private final PointReservationRepository pointReservationRepository;
  private final PointTransactionRepository pointTransactionRepository;
  private final PointLockService pointLockService;
  private final SettlementRepository settlementRepository;

  @Transactional
  public void settleAuction(
      Long auctionId, Long winnerMemberId, Long sellerMemberId, Long winningPrice) {
    if (winnerMemberId == null) {
      log.info("유찰된 경매라 정산을 건너뜀 - auctionId={}", auctionId);
      return;
    }

    boolean winnerSettled =
        !isAlreadySettled(auctionId, winnerMemberId, SettlementType.WINNER_PAYMENT);
    boolean sellerSettled =
        !isAlreadySettled(auctionId, sellerMemberId, SettlementType.SELLER_PAYOUT);
    if (!winnerSettled && !sellerSettled) {
      return;
    }

    Auction auction = auctionManageService.getAuctionById(auctionId);
    Member winner = memberManageService.getMemberById(winnerMemberId);
    Member seller = memberManageService.getMemberById(sellerMemberId);
    if (winnerSettled) {
      saveSettlement(auction, winner, SettlementType.WINNER_PAYMENT, winningPrice);
    }
    if (sellerSettled) {
      saveSettlement(auction, seller, SettlementType.SELLER_PAYOUT, winningPrice);
    }

    PointReservation reservation =
        pointReservationRepository.findByAuctionIdForUpdate(auctionId).orElse(null);
    Map<Long, Point> points =
        pointLockService.lockPoints(
            Arrays.asList(
                winnerSettled ? winnerMemberId : null, sellerSettled ? sellerMemberId : null));

    if (winnerSettled) {
      payWinningBid(auction, winner, points.get(winnerMemberId), reservation, winningPrice);
    }
    if (sellerSettled) {
      paySeller(auction, seller, points.get(sellerMemberId), winningPrice);
    }
  }

  private void saveSettlement(
      Auction auction, Member member, SettlementType settlementType, Long amount) {
    settlementRepository.save(Settlement.create(auction, member, settlementType, amount));
    log.info(
        "정산 처리 - auctionId={}, memberId={}, settlementType={}, amount={}",
        auction.getAuctionId(),
        member.getMemberId(),
        settlementType,
        amount);
  }

  private boolean isAlreadySettled(Long auctionId, Long memberId, SettlementType settlementType) {
    boolean alreadySettled =
        settlementRepository.existsByAuctionIdAndMemberIdAndSettlementType(
            auctionId, memberId, settlementType);
    if (alreadySettled) {
      log.info(
          "이미 처리된 정산이라 건너뜀 - auctionId={}, memberId={}, settlementType={}",
          auctionId,
          memberId,
          settlementType);
    }
    return alreadySettled;
  }

  private void payWinningBid(
      Auction auction, Member winner, Point point, PointReservation reservation, long amount) {
    if (reservation == null) {
      if (!auction.isLegacyUnreservedBid()) {
        throw new IllegalStateException("낙찰 경매의 포인트 예약을 찾을 수 없습니다.");
      }
      point.decreaseBalance(amount);
    } else {
      if (reservation.getReservationStatus() != ACTIVE
          || !reservation.getMember().getMemberId().equals(winner.getMemberId())
          || reservation.getAmount() != amount) {
        throw new IllegalStateException("낙찰 정보와 포인트 예약이 일치하지 않습니다.");
      }
      point.capture(amount);
      reservation.capture();
      pointReservationRepository.save(reservation);
    }
    pointRepository.save(point);
    pointTransactionRepository.save(
        PointTransaction.forAuctionPayment(winner, amount, point.getBalance(), auction));
  }

  private void paySeller(Auction auction, Member seller, Point point, long amount) {
    point.increaseBalance(amount);
    pointRepository.save(point);
    pointTransactionRepository.save(
        PointTransaction.forAuctionPayout(seller, amount, point.getBalance(), auction));
  }
}
