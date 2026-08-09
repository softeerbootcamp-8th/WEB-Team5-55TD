package com.ootd.pickup.auction.scheduler;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.bid.domain.QBid.bid;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;
import static com.ootd.pickup.member.domain.QMember.member;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/**
 * 경매 스케줄러의 상태 전이. 대상 조회는 {@link AuctionSchedulerJpaRepository}에 위임하고, 전이(bulk update)와 연관 조회는
 * QueryDSL로 직접 실행한다.
 *
 * <p>전이 메서드는 도착 상태를 파라미터로 받지 않고 이름에 박아 둔다. 낙찰과 유찰은 조건이 서로 다르므로, 상태를 넘길 수 있게 하면 조건과 도착 상태가 어긋난 조합을
 * 호출할 수 있다.
 *
 * <p>bulk update는 영속성 컨텍스트를 거치지 않고 DB를 직접 갱신해, 같은 트랜잭션에서 이미 로드된 엔티티가 stale해진다. 그래서 매번 갱신 직후 컨텍스트를
 * 비운다.
 */
@Repository
@RequiredArgsConstructor
public class AuctionSchedulerRepository {

  private final AuctionSchedulerJpaRepository auctionSchedulerJpaRepository;
  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  public List<Long> findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit) {
    return auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
        auctionStatus, limit);
  }

  public List<Long> findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit) {
    return auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
        auctionStatus, limit);
  }

  /** 예정 상태인 경매를 진행 중으로 전이시킨다. {@code auctionStatus}만 갱신해 마감 직전 입찰이 쓴 {@code winningPrice}를 지킨다. */
  public int updateAuctionStatusToOngoingByIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.auctionStatus, AuctionStatus.ONGOING)
                .where(
                    auction.auctionId.in(auctionIds),
                    auction.auctionStatus.eq(AuctionStatus.SCHEDULED))
                .execute();
    entityManager.clear();
    return updated;
  }

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

  /** 리저브를 채운 경매를 낙찰로 전이시킨다. 리저브 비교를 DB에서 해, 전이 직전 들어온 입찰도 판정에 반영한다. */
  public int updateAuctionStatusToWonByIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.auctionStatus, AuctionStatus.WON)
                .where(
                    auction.auctionId.in(auctionIds),
                    auction.auctionStatus.eq(AuctionStatus.ONGOING),
                    auction.winningPrice.isNotNull(),
                    auction.winningPrice.goe(auction.reservePrice))
                .execute();
    entityManager.clear();
    return updated;
  }

  /**
   * 리저브를 채우지 못한 경매를 유찰로 전이시킨다. 입찰이 없어 {@code winningPrice}가 null인 경우도 유찰이다. {@link
   * #updateAuctionStatusToWonByIdIn}의 조건과 배타적이면서 둘을 합치면 모든 경우를 덮는다.
   */
  public int updateAuctionStatusToPassedByIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.auctionStatus, AuctionStatus.PASSED)
                .where(
                    auction.auctionId.in(auctionIds),
                    auction.auctionStatus.eq(AuctionStatus.ONGOING),
                    auction.winningPrice.isNull().or(auction.winningPrice.lt(auction.reservePrice)))
                .execute();
    entityManager.clear();
    return updated;
  }

  /** 낙찰로 전이된 경매의 위탁 상품을 판매 완료로 전이시킨다. {@link #updateAuctionStatusToWonByIdIn} 바로 다음에 불려야 한다. */
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

  /** 이벤트 조립에 필요한 연관까지 함께 경매를 조회한다. 소비자가 트랜잭션 밖에서 실행되므로 지연 로딩 대신 fetch join으로 미리 채운다. */
  public List<Auction> findAllWithConsignmentAndSellerMemberByIdIn(List<Long> auctionIds) {
    return queryFactory
        .selectFrom(auction)
        .join(auction.consignment, consignment)
        .fetchJoin()
        .join(consignment.sellerMember, member)
        .fetchJoin()
        .where(auction.auctionId.in(auctionIds))
        .fetch();
  }

  /** 낙찰 이벤트에 낙찰자 식별자를 담기 위해 입찰을 입찰자와 함께 조회한다. */
  public List<Bid> findAllBidsWithMemberByIdIn(List<Long> bidIds) {
    return queryFactory
        .selectFrom(bid)
        .join(bid.member, member)
        .fetchJoin()
        .where(bid.bidId.in(bidIds))
        .fetch();
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
