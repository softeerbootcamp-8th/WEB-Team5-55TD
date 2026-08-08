package com.ootd.pickup.consignments.repository.consignment;

import static com.ootd.pickup.cards.domain.QCard.*;
import static com.ootd.pickup.consignments.domain.QConsignment.*;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
  public List<Consignment> findAllBySellerMemberIdAndStatusAndCursor(
      Long sellerMemberId, ConsignmentStatus status, Long cursor, int size) {
    return queryFactory
        .selectFrom(consignment)
        .join(consignment.card, card)
        .fetchJoin()
        .where(
            consignment.sellerMember.memberId.eq(sellerMemberId),
            statusEq(status),
            consignmentIdLt(cursor))
        .orderBy(consignment.consignmentId.desc())
        .limit(size)
        .fetch();
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
}
