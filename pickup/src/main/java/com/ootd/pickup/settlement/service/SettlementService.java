package com.ootd.pickup.settlement.service;

import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.service.AuctionManageService;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.settlement.domain.Settlement;
import com.ootd.pickup.settlement.domain.SettlementType;
import com.ootd.pickup.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>포인트 잔액 갱신은 {@link PointRepository#findByMemberIdForUpdate}로 행 락을 잡는다. 서로 다른 경매의 정산이 동시에 처리되며
 * 같은 회원의 포인트를 건드릴 수 있어, 락 없이는 동시 갱신 하나가 유실될 수 있다(lost update). 또한 두 회원의 락을 잡는 순서가 경매마다 "낙찰자 먼저"로
 * 고정돼 있으면, 경매 A는 회원1→회원2, 경매 B는 회원2→회원1 순으로 잠그려 할 때 교착상태가 날 수 있으므로 항상 memberId 오름차순으로 잠근다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SettlementService {

  private final AuctionManageService auctionManageService;
  private final MemberManageService memberManageService;
  private final PointRepository pointRepository;
  private final SettlementRepository settlementRepository;

  @Transactional
  public void settleAuction(
      Long auctionId, Long winnerMemberId, Long sellerMemberId, Long winningPrice) {
    if (winnerMemberId == null) {
      log.info("유찰된 경매라 정산을 건너뜀 - auctionId={}", auctionId);
      return;
    }

    boolean winnerSettled =
        trySettle(auctionId, winnerMemberId, SettlementType.WINNER_PAYMENT, winningPrice);
    boolean sellerSettled =
        trySettle(auctionId, sellerMemberId, SettlementType.SELLER_PAYOUT, winningPrice);

    adjustBalancesInLockOrder(
        winnerMemberId, winnerSettled, sellerMemberId, sellerSettled, winningPrice);
  }

  private boolean trySettle(
      Long auctionId, Long memberId, SettlementType settlementType, Long amount) {
    if (isAlreadySettled(auctionId, memberId, settlementType)) {
      return false;
    }
    settle(auctionId, memberId, settlementType, amount);
    return true;
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

  private void settle(Long auctionId, Long memberId, SettlementType settlementType, Long amount) {
    Auction auction = auctionManageService.getAuctionById(auctionId);
    Member member = memberManageService.getMemberById(memberId);
    settlementRepository.save(Settlement.create(auction, member, settlementType, amount));
    log.info(
        "정산 처리 - auctionId={}, memberId={}, settlementType={}, amount={}",
        auctionId,
        memberId,
        settlementType,
        amount);
  }

  private void adjustBalancesInLockOrder(
      Long winnerMemberId,
      boolean winnerSettled,
      Long sellerMemberId,
      boolean sellerSettled,
      Long amount) {
    if (winnerMemberId < sellerMemberId) {
      if (winnerSettled) {
        decreaseBalance(winnerMemberId, amount);
      }
      if (sellerSettled) {
        increaseBalance(sellerMemberId, amount);
      }
    } else {
      if (sellerSettled) {
        increaseBalance(sellerMemberId, amount);
      }
      if (winnerSettled) {
        decreaseBalance(winnerMemberId, amount);
      }
    }
  }

  private void decreaseBalance(Long memberId, Long amount) {
    Point point = getPointForUpdate(memberId);
    point.decreaseBalance(amount);
    pointRepository.save(point);
  }

  private void increaseBalance(Long memberId, Long amount) {
    Point point = getPointForUpdate(memberId);
    point.increaseBalance(amount);
    pointRepository.save(point);
  }

  private Point getPointForUpdate(Long memberId) {
    return pointRepository
        .findByMemberIdForUpdate(memberId)
        .orElseThrow(() -> new PickUpException(POINT_NOT_FOUND));
  }
}
