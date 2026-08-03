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
import com.querydsl.jpa.impl.JPAQueryFactory;
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
    return auctionJpaRepository.findByIdForUpdate(auctionId);
  }

  @Override
  public Map<Long, Long> findAuctionIdsByConsignmentIn(List<Consignment> consignments) {
    if (consignments.isEmpty()) {
      return Map.of();
    }

    List<Tuple> rows =
        queryFactory
            .select(auction.consignment.consignmentId, auction.auctionId)
            .from(auction)
            .where(auction.consignment.in(consignments))
            .fetch();

    return rows.stream()
        .collect(
            Collectors.toMap(
                row -> row.get(auction.consignment.consignmentId),
                row -> row.get(auction.auctionId)));
  }
}
