package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.auction.domain.QWatch.watch;
import static com.ootd.pickup.cards.domain.QCard.card;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;
import static com.ootd.pickup.member.domain.QMember.member;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.global.util.EpochMillis;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class AuctionDataJpaRepository implements AuctionRepository {

  private final AuctionJpaRepository auctionJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Auction save(Auction newAuction) {
    return auctionJpaRepository.save(newAuction);
  }

  @Override
  public Optional<Auction> findById(Long auctionId) {
    return auctionJpaRepository.findById(auctionId);
  }

  @Override
  public Optional<Auction> findByIdWithConsignmentAndCard(Long auctionId) {
    return Optional.ofNullable(
        queryFactory
            .selectFrom(auction)
            .join(auction.consignment, consignment)
            .fetchJoin()
            .join(consignment.card, card)
            .fetchJoin()
            .join(consignment.sellerMember, member)
            .fetchJoin()
            .where(auction.auctionId.eq(auctionId))
            .fetchOne());
  }

  @Override
  public List<Auction> searchAuctions(
      String q, List<AuctionStatus> statuses, AuctionSort sort, AuctionCursor cursor, int limit) {
    return queryFactory
        .selectFrom(auction)
        .join(auction.consignment, consignment)
        .fetchJoin()
        .join(consignment.card, card)
        .fetchJoin()
        .where(keywordMatches(q), statusIn(statuses), keysetPredicate(sort, cursor))
        .orderBy(orderSpecifiers(sort))
        .limit(limit)
        .fetch();
  }

  private BooleanExpression keywordMatches(String q) {
    if (!StringUtils.hasText(q)) {
      return null;
    }

    BooleanExpression nameOrSet =
        card.cardName.containsIgnoreCase(q).or(card.setName.containsIgnoreCase(q));
    return matchLanguage(q)
        .map(language -> nameOrSet.or(card.language.eq(language)))
        .orElse(nameOrSet);
  }

  private Optional<Language> matchLanguage(String q) {
    String trimmed = q.trim();
    return Arrays.stream(Language.values())
        .filter(language -> language.getDisplayName().equalsIgnoreCase(trimmed))
        .findFirst();
  }

  private BooleanExpression statusIn(List<AuctionStatus> statuses) {
    if (statuses == null || statuses.isEmpty()) {
      return null;
    }

    return auction.auctionStatus.in(statuses);
  }

  private NumberExpression<Long> watchCountExpression() {
    return Expressions.asNumber(
        JPAExpressions.select(watch.count())
            .from(watch)
            .where(watch.auction.auctionId.eq(auction.auctionId)));
  }

  private DateTimeExpression<LocalDateTime> endedAtSortExpression() {
    return auction.endedAt.coalesce(AuctionCursor.SENTINEL_END_AT);
  }

  private OrderSpecifier<?>[] orderSpecifiers(AuctionSort sort) {
    return switch (sort) {
      case POPULAR ->
          new OrderSpecifier<?>[] {watchCountExpression().desc(), auction.auctionId.desc()};
      case PRICE_ASC ->
          new OrderSpecifier<?>[] {auction.startingPrice.asc(), auction.auctionId.asc()};
      case PRICE_DESC ->
          new OrderSpecifier<?>[] {auction.startingPrice.desc(), auction.auctionId.desc()};
      case ENDING_SOON ->
          new OrderSpecifier<?>[] {endedAtSortExpression().asc(), auction.auctionId.asc()};
      case STARTING_SOON ->
          new OrderSpecifier<?>[] {auction.startedAt.asc(), auction.auctionId.asc()};
      case RECENT -> new OrderSpecifier<?>[] {auction.createdAt.desc(), auction.auctionId.desc()};
    };
  }

  private BooleanExpression keysetPredicate(AuctionSort sort, AuctionCursor cursor) {
    if (cursor == null) {
      return null;
    }

    return switch (sort) {
      case POPULAR ->
          watchCountExpression()
              .lt(cursor.sortValue())
              .or(
                  watchCountExpression()
                      .eq(cursor.sortValue())
                      .and(auction.auctionId.lt(cursor.auctionId())));
      case PRICE_ASC ->
          auction
              .startingPrice
              .gt(cursor.sortValue())
              .or(
                  auction
                      .startingPrice
                      .eq(cursor.sortValue())
                      .and(auction.auctionId.gt(cursor.auctionId())));
      case PRICE_DESC ->
          auction
              .startingPrice
              .lt(cursor.sortValue())
              .or(
                  auction
                      .startingPrice
                      .eq(cursor.sortValue())
                      .and(auction.auctionId.lt(cursor.auctionId())));
      case ENDING_SOON -> {
        LocalDateTime value = EpochMillis.toLocalDateTime(cursor.sortValue());
        yield endedAtSortExpression()
            .gt(value)
            .or(endedAtSortExpression().eq(value).and(auction.auctionId.gt(cursor.auctionId())));
      }
      case STARTING_SOON -> {
        LocalDateTime value = EpochMillis.toLocalDateTime(cursor.sortValue());
        yield auction
            .startedAt
            .gt(value)
            .or(auction.startedAt.eq(value).and(auction.auctionId.gt(cursor.auctionId())));
      }
      case RECENT -> {
        LocalDateTime value = EpochMillis.toLocalDateTime(cursor.sortValue());
        yield auction
            .createdAt
            .lt(value)
            .or(auction.createdAt.eq(value).and(auction.auctionId.lt(cursor.auctionId())));
      }
    };
  }

  @Override
  public Optional<Auction> findByIdForUpdate(Long auctionId) {
    return Optional.ofNullable(
        ((JPAQuery<Auction>)
                queryFactory
                    .selectFrom(auction)
                    .join(auction.consignment, consignment)
                    .fetchJoin()
                    .join(consignment.sellerMember, member)
                    .fetchJoin()
                    .where(auction.auctionId.eq(auctionId)))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  @Override
  public long countBySellerMemberIdAndStatus(Long sellerMemberId, AuctionStatus status) {
    return queryFactory
        .select(auction.count())
        .from(auction)
        .where(
            auction.consignment.sellerMember.memberId.eq(sellerMemberId),
            auction.auctionStatus.eq(status))
        .fetchOne();
  }

  @Override
  public Map<Long, AuctionSummary> findAuctionSummariesByConsignmentIn(
      List<Consignment> consignments) {
    if (consignments.isEmpty()) {
      return Map.of();
    }

    // 재신청 시 같은 위탁 상품에 새 경매가 또 생성되므로, 위탁 상품 하나에 경매가 여러 건 연결될 수 있다.
    // 그중 가장 최근(auctionId가 가장 큰) 경매 하나만 대표로 남긴다. 위탁 상품별 최댓값을 서브쿼리로 먼저
    // 구해 그 auctionId만 조인하면, 상품당 과거 경매를 전부 내려받아 애플리케이션에서 추리지 않아도 된다.
    List<Tuple> rows =
        queryFactory
            .select(
                auction.consignment.consignmentId,
                auction.auctionId,
                auction.auctionStatus,
                auction.startedAt,
                auction.endedAt)
            .from(auction)
            .where(
                auction.auctionId.in(
                    JPAExpressions.select(auction.auctionId.max())
                        .from(auction)
                        .where(auction.consignment.in(consignments))
                        .groupBy(auction.consignment.consignmentId)))
            .fetch();

    return rows.stream()
        .collect(
            Collectors.toMap(
                row -> row.get(auction.consignment.consignmentId),
                row ->
                    new AuctionSummary(
                        row.get(auction.auctionId),
                        row.get(auction.auctionStatus),
                        row.get(auction.startedAt),
                        row.get(auction.endedAt))));
  }

  @Override
  public List<Auction> findAllBySellerMemberIdWithCard(
      Long sellerMemberId, List<AuctionStatus> statuses, SalesCursor cursor, int limit) {
    return queryFactory
        .selectFrom(auction)
        .join(auction.consignment, consignment)
        .fetchJoin()
        .join(consignment.card, card)
        .fetchJoin()
        .where(
            consignment.sellerMember.memberId.eq(sellerMemberId),
            statusIn(statuses),
            salesKeysetPredicate(cursor))
        .orderBy(auction.endedAt.desc(), auction.auctionId.desc())
        .limit(limit)
        .fetch();
  }

  private BooleanExpression salesKeysetPredicate(SalesCursor cursor) {
    if (cursor == null) {
      return null;
    }

    LocalDateTime endedAt = EpochMillis.toLocalDateTime(cursor.endedAtEpochMillis());
    return auction
        .endedAt
        .lt(endedAt)
        .or(auction.endedAt.eq(endedAt).and(auction.auctionId.lt(cursor.auctionId())));
  }
}
