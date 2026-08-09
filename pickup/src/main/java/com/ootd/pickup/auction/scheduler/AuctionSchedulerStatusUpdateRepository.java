package com.ootd.pickup.auction.scheduler;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.bid.domain.QBid.bid;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 위탁 상품·입찰의 경매 종료 연쇄 전이. QueryDSL bulk update로 실행한다.
 *
 * <p>{@link AuctionSchedulerJpaRepository}의 경매 상태 전이와 같은 이유로 bulk update만 쓴다: 영속성 컨텍스트를 거치지 않아야 동시에
 * 들어오는 입찰과 경쟁하지 않는다. 갱신 직후 컨텍스트를 비워 stale 엔티티를 남기지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AuctionSchedulerStatusUpdateRepository {

  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  /** 경매가 진행 중으로 전이된 위탁 상품을 함께 진행 중으로 전이시킨다. */
  public int updateConsignmentStatusToAuctionOngoingByAuctionIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(consignment)
                .set(consignment.status, ConsignmentStatus.AUCTION_ONGOING)
                .where(
                    consignment.consignmentId.in(consignmentIdsOf(auctionIds)),
                    consignment.status.eq(ConsignmentStatus.AUCTION_SCHEDULED))
                .execute();
    entityManager.clear();
    return updated;
  }

  /** 낙찰로 전이된 경매의 위탁 상품을 판매 완료로 전이시킨다. 호출 시점에 이미 대상 경매가 WON으로 전이돼 있어야 한다. */
  public int updateConsignmentStatusToWonByAuctionIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(consignment)
                .set(consignment.status, ConsignmentStatus.WON)
                .where(
                    consignment.consignmentId.in(consignmentIdsOf(auctionIds, AuctionStatus.WON)),
                    consignment.status.eq(ConsignmentStatus.AUCTION_ONGOING))
                .execute();
    entityManager.clear();
    return updated;
  }

  /**
   * 유찰로 전이된 경매의 위탁 상품을 재등록 가능(유찰) 상태로 전이시킨다. {@code ConsignmentStatus.PASSED}는 재등록이 가능한 상태다({@code
   * isModifiable}).
   */
  public int updateConsignmentStatusToPassedByAuctionIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(consignment)
                .set(consignment.status, ConsignmentStatus.PASSED)
                .where(
                    consignment.consignmentId.in(
                        consignmentIdsOf(auctionIds, AuctionStatus.PASSED)),
                    consignment.status.eq(ConsignmentStatus.AUCTION_ONGOING))
                .execute();
    entityManager.clear();
    return updated;
  }

  /**
   * 낙찰이 확정된 입찰을 {@code WON}으로 전이시킨다. 갱신하지 않으면 낙찰 내역 조회({@code bidStatus = WON} 필터)가 항상 빈 결과를 반환한다.
   */
  public int updateBidStatusToWonByIdIn(List<Long> bidIds) {
    int updated =
        (int)
            queryFactory
                .update(bid)
                .set(bid.bidStatus, BidStatus.WON)
                .where(bid.bidId.in(bidIds))
                .execute();
    entityManager.clear();
    return updated;
  }

  private JPQLQuery<Long> consignmentIdsOf(List<Long> auctionIds) {
    return JPAExpressions.select(auction.consignment.consignmentId)
        .from(auction)
        .where(auction.auctionId.in(auctionIds));
  }

  private JPQLQuery<Long> consignmentIdsOf(List<Long> auctionIds, AuctionStatus auctionStatus) {
    return JPAExpressions.select(auction.consignment.consignmentId)
        .from(auction)
        .where(auction.auctionId.in(auctionIds), auction.auctionStatus.eq(auctionStatus));
  }
}
