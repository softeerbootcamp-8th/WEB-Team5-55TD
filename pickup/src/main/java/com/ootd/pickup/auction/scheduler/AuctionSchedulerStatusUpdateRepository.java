package com.ootd.pickup.auction.scheduler;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 위탁 상품의 경매 종료 연쇄 전이. QueryDSL bulk update로 실행한다.
 *
 * <p>{@link AuctionSchedulerJpaRepository}의 경매 상태 전이와 같은 이유로 bulk update만 쓴다: 영속성 컨텍스트를 거치지 않아야 동시에
 * 들어오는 입찰과 경쟁하지 않는다. 갱신 직후 컨텍스트를 비워 stale 엔티티를 남기지 않는다.
 *
 * <p>경매 시작(SCHEDULED→ONGOING) 시점에는 위탁 상품을 갱신할 필요가 없다 — {@code ConsignmentStatus}는 {@code
 * AUCTION_SCHEDULED}/{@code AUCTION_ONGOING} 구분 없이 {@code IN_AUCTION} 하나로 경매 신청부터 종료까지를 나타낸다.
 */
@Repository
@RequiredArgsConstructor
public class AuctionSchedulerStatusUpdateRepository {

  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  /** 낙찰로 전이된 경매의 위탁 상품을 판매 완료로 전이시킨다. 호출 시점에 이미 대상 경매가 WON으로 전이돼 있어야 한다. */
  public int updateConsignmentStatusToSoldByAuctionIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(consignment)
                .set(consignment.status, ConsignmentStatus.SOLD)
                .where(
                    consignment.consignmentId.in(consignmentIdsOf(auctionIds, AuctionStatus.WON)),
                    consignment.status.eq(ConsignmentStatus.IN_AUCTION))
                .execute();
    entityManager.clear();
    return updated;
  }

  /** 유찰로 전이된 경매의 위탁 상품을 등록 가능 상태로 되돌린다. 유찰(재신청 가능)과 신규 등록은 {@code ConsignmentStatus}에서 같은 값을 쓴다. */
  public int updateConsignmentStatusToRegisterableByAuctionIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(consignment)
                .set(consignment.status, ConsignmentStatus.REGISTERABLE)
                .where(
                    consignment.consignmentId.in(
                        consignmentIdsOf(auctionIds, AuctionStatus.PASSED)),
                    consignment.status.eq(ConsignmentStatus.IN_AUCTION))
                .execute();
    entityManager.clear();
    return updated;
  }

  private JPQLQuery<Long> consignmentIdsOf(List<Long> auctionIds, AuctionStatus auctionStatus) {
    return JPAExpressions.select(auction.consignment.consignmentId)
        .from(auction)
        .where(auction.auctionId.in(auctionIds), auction.auctionStatus.eq(auctionStatus));
  }
}
