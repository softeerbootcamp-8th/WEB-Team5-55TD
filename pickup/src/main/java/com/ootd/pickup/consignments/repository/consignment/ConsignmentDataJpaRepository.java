package com.ootd.pickup.consignments.repository.consignment;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.cards.domain.QCard.*;
import static com.ootd.pickup.consignments.domain.QConsignment.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.domain.QAuction;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConsignmentDataJpaRepository implements ConsignmentRepository {

  private final ConsignmentJpaRepository consignmentJpaRepository;
  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  @Override
  public Consignment save(Consignment consignment) {
    return consignmentJpaRepository.save(consignment);
  }

  @Override
  public Optional<Consignment> findConsignmentById(Long consignmentId) {
    return consignmentJpaRepository.findById(consignmentId);
  }

  @Override
  public Optional<Consignment> findByIdForUpdate(Long consignmentId) {
    return Optional.ofNullable(
        ((JPAQuery<Consignment>)
                queryFactory
                    .selectFrom(consignment)
                    .where(consignment.consignmentId.eq(consignmentId)))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  @Override
  public void deleteById(Long consignmentId) {
    consignmentJpaRepository.deleteById(consignmentId);
  }

  @Override
  public long countBySellerMemberId(Long sellerMemberId) {
    return queryFactory
        .select(consignment.count())
        .from(consignment)
        .where(consignment.sellerMember.memberId.eq(sellerMemberId))
        .fetchOne();
  }

  @Override
  public List<Consignment> findAllBySellerMemberIdAndStatusAndLatestAuctionStatusAndCursor(
      Long sellerMemberId,
      ConsignmentStatus status,
      AuctionStatus latestAuctionStatus,
      Long cursor,
      int size) {
    return queryFactory
        .selectFrom(consignment)
        .join(consignment.card, card)
        .fetchJoin()
        .where(
            consignment.sellerMember.memberId.eq(sellerMemberId),
            statusEq(status),
            latestAuctionStatusEq(latestAuctionStatus),
            consignmentIdLt(cursor))
        .orderBy(consignment.consignmentId.desc())
        .limit(size)
        .fetch();
  }

  private BooleanExpression latestAuctionStatusEq(AuctionStatus latestAuctionStatus) {
    if (latestAuctionStatus == null) {
      return null;
    }

    QAuction targetAuction = new QAuction("targetAuction");
    QAuction latestAuction = new QAuction("latestAuction");
    return JPAExpressions.selectOne()
        .from(targetAuction)
        .where(
            targetAuction.consignment.eq(consignment),
            targetAuction.auctionStatus.eq(latestAuctionStatus),
            targetAuction.auctionId.eq(
                JPAExpressions.select(latestAuction.auctionId.max())
                    .from(latestAuction)
                    .where(latestAuction.consignment.eq(consignment))))
        .exists();
  }

  private BooleanExpression statusEq(ConsignmentStatus status) {
    if (status == null) {
      return null;
    }

    return consignment.status.eq(status);
  }

  private BooleanExpression consignmentIdLt(Long cursor) {
    if (cursor == null) {
      return null;
    }

    return consignment.consignmentId.lt(cursor);
  }

  @Override
  public boolean existsBySellerMemberIdAndStatus(
      Long sellerMemberId, ConsignmentStatus consignmentStatus) {
    return consignmentJpaRepository.existsBySellerMember_MemberIdAndStatus(
        sellerMemberId, consignmentStatus);
  }

  @Override
  public int updateStatusToSoldByAuctionIdIn(List<Long> auctionIds) {
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

  @Override
  public int updateStatusToRegisterableByAuctionIdIn(List<Long> auctionIds) {
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
