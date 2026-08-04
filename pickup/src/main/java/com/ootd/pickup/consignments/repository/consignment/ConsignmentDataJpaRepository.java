package com.ootd.pickup.consignments.repository.consignment;

import static com.ootd.pickup.cards.domain.QCard.*;
import static com.ootd.pickup.consignments.domain.QConsignment.*;
import static com.ootd.pickup.member.domain.QMember.member;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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
    return consignmentJpaRepository.findByIdForUpdate(consignmentId);
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

  @Override
  public Page<Consignment> searchConsignmentsForAdmin(
      String q, List<ConsignmentStatus> statuses, Long sellerMemberId, Pageable pageable) {
    List<Consignment> content =
        queryFactory
            .selectFrom(consignment)
            .join(consignment.card, card)
            .fetchJoin()
            .join(consignment.sellerMember, member)
            .fetchJoin()
            .where(keywordMatches(q), statusIn(statuses), sellerEq(sellerMemberId))
            .orderBy(consignment.consignmentId.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    Long total =
        queryFactory
            .select(consignment.count())
            .from(consignment)
            .join(consignment.card, card)
            .where(keywordMatches(q), statusIn(statuses), sellerEq(sellerMemberId))
            .fetchOne();

    return new PageImpl<>(content, pageable, total == null ? 0 : total);
  }

  private BooleanExpression keywordMatches(String q) {
    if (!StringUtils.hasText(q)) {
      return null;
    }

    return card.cardName.containsIgnoreCase(q).or(card.setName.containsIgnoreCase(q));
  }

  private BooleanExpression statusIn(List<ConsignmentStatus> statuses) {
    if (statuses == null || statuses.isEmpty()) {
      return null;
    }

    return consignment.status.in(statuses);
  }

  private BooleanExpression sellerEq(Long sellerMemberId) {
    if (sellerMemberId == null) {
      return null;
    }

    return consignment.sellerMember.memberId.eq(sellerMemberId);
  }
}
