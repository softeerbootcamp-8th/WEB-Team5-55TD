package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.bid.domain.QBid.bid;
import static com.ootd.pickup.cards.domain.QCard.card;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;
import static com.ootd.pickup.member.domain.QMember.member;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionSchedulePolicy;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.global.util.EpochMillis;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class AuctionDataJpaRepository implements AuctionRepository {

  private final AuctionJpaRepository auctionJpaRepository;
  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  @Override
  public Auction save(Auction newAuction) {
    return auctionJpaRepository.save(newAuction);
  }

  @Override
  public Optional<Auction> findById(Long auctionId) {
    return auctionJpaRepository.findById(auctionId);
  }

  @Override
  public int incrementWatchCountById(Long auctionId) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.watchCount, auction.watchCount.add(1L))
                .where(auction.auctionId.eq(auctionId))
                .execute();
    entityManager.clear();
    return updated;
  }

  @Override
  public int decrementWatchCountById(Long auctionId) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.watchCount, auction.watchCount.subtract(1L))
                .where(auction.auctionId.eq(auctionId), auction.watchCount.gt(0L))
                .execute();
    entityManager.clear();
    return updated;
  }

  @Override
  public int resetWatchCountById(Long auctionId) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.watchCount, 0L)
                .where(auction.auctionId.eq(auctionId))
                .execute();
    entityManager.clear();
    return updated;
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
      String q,
      AuctionSearchField searchField,
      List<AuctionStatus> statuses,
      AuctionSort sort,
      AuctionCursor cursor,
      int limit,
      Long sellerId,
      Long cardId,
      Long excludeAuctionId) {
    return queryFactory
        .selectFrom(auction)
        .join(auction.consignment, consignment)
        .fetchJoin()
        .join(consignment.card, card)
        .fetchJoin()
        // 목록 항목이 판매자 닉네임을 함께 내려주므로 같이 가져온다(N+1 방지).
        .join(consignment.sellerMember, member)
        .fetchJoin()
        .where(
            keywordMatches(q, searchField),
            statusIn(statuses),
            sellerIdEq(sellerId),
            cardIdEq(cardId),
            auctionIdNotEq(excludeAuctionId),
            keysetPredicate(sort, cursor))
        .orderBy(orderSpecifiers(sort))
        .limit(limit)
        .fetch();
  }

  private BooleanExpression sellerIdEq(Long sellerId) {
    return sellerId == null ? null : consignment.sellerMember.memberId.eq(sellerId);
  }

  private BooleanExpression cardIdEq(Long cardId) {
    return cardId == null ? null : card.cardId.eq(cardId);
  }

  private BooleanExpression auctionIdNotEq(Long excludeAuctionId) {
    return excludeAuctionId == null ? null : auction.auctionId.ne(excludeAuctionId);
  }

  private BooleanExpression keywordMatches(String q, AuctionSearchField searchField) {
    if (!StringUtils.hasText(q)) {
      return null;
    }

    return switch (searchField) {
      case AUCTION_TITLE -> auction.title.containsIgnoreCase(q);
      case CARD_NAME -> card.cardName.containsIgnoreCase(q);
      case SELLER -> sellerNicknameMatches(q);
      case ALL -> anyFieldMatches(q);
    };
  }

  private BooleanExpression anyFieldMatches(String q) {
    BooleanExpression matched =
        auction
            .title
            .containsIgnoreCase(q)
            .or(card.cardName.containsIgnoreCase(q))
            .or(card.setName.containsIgnoreCase(q))
            .or(sellerNicknameMatches(q));
    return matchLanguage(q).map(language -> matched.or(card.language.eq(language))).orElse(matched);
  }

  private BooleanExpression sellerNicknameMatches(String q) {
    return consignment.sellerMember.nickname.containsIgnoreCase(q);
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

  private DateTimeExpression<LocalDateTime> endedAtSortExpression() {
    return auction.endedAt.coalesce(AuctionCursor.SENTINEL_END_AT);
  }

  private NumberExpression<Long> currentPriceSortExpression() {
    return auction.winningPrice.coalesce(auction.startingPrice);
  }

  private OrderSpecifier<?>[] orderSpecifiers(AuctionSort sort) {
    return switch (sort) {
      case POPULAR -> new OrderSpecifier<?>[] {auction.watchCount.desc(), auction.auctionId.desc()};
      case PRICE_ASC ->
          new OrderSpecifier<?>[] {currentPriceSortExpression().asc(), auction.auctionId.asc()};
      case PRICE_DESC ->
          new OrderSpecifier<?>[] {currentPriceSortExpression().desc(), auction.auctionId.desc()};
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
          auction
              .watchCount
              .lt(cursor.sortValue())
              .or(
                  auction
                      .watchCount
                      .eq(cursor.sortValue())
                      .and(auction.auctionId.lt(cursor.auctionId())));
      case PRICE_ASC ->
          currentPriceSortExpression()
              .gt(cursor.sortValue())
              .or(
                  currentPriceSortExpression()
                      .eq(cursor.sortValue())
                      .and(auction.auctionId.gt(cursor.auctionId())));
      case PRICE_DESC ->
          currentPriceSortExpression()
              .lt(cursor.sortValue())
              .or(
                  currentPriceSortExpression()
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
  public boolean extendEndAtIfClosingSoon(Auction targetAuction, LocalDateTime bidAt) {
    LocalDateTime currentEndAt = targetAuction.getEndedAt();
    if (currentEndAt == null) {
      return false;
    }

    LocalDateTime softCloseBoundary = bidAt.plus(AuctionSchedulePolicy.SOFT_CLOSE_WINDOW);
    LocalDateTime extendedEndAt = currentEndAt.plus(AuctionSchedulePolicy.SOFT_CLOSE_WINDOW);
    long updatedRows =
        new JPAUpdateClause(entityManager, auction)
            .set(auction.endedAt, extendedEndAt)
            .where(
                auction.auctionId.eq(targetAuction.getAuctionId()),
                auction.auctionStatus.eq(AuctionStatus.ONGOING),
                auction.endedAt.eq(currentEndAt),
                auction.endedAt.gt(bidAt),
                auction.endedAt.lt(softCloseBoundary))
            .execute();
    if (updatedRows != 1) {
      return false;
    }

    // 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 이후 조회가 이전 종료 시각을 보지 않게 한다.
    entityManager.clear();
    targetAuction.extendEndAtBySoftCloseWindow();
    return true;
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
                auction.title,
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
                        row.get(auction.title),
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

  @Override
  public List<Long> findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit) {
    return queryFactory
        .select(auction.auctionId)
        .from(auction)
        .where(
            auction.auctionStatus.eq(auctionStatus),
            auction.startedAt.loe(DateTimeExpression.currentTimestamp(LocalDateTime.class)))
        .orderBy(auction.startedAt.asc(), auction.auctionId.asc())
        .limit(limit.max())
        .fetch();
  }

  @Override
  public List<Long> findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit) {
    return queryFactory
        .select(auction.auctionId)
        .from(auction)
        .where(
            auction.auctionStatus.eq(auctionStatus),
            auction.endedAt.loe(DateTimeExpression.currentTimestamp(LocalDateTime.class)))
        .orderBy(auction.endedAt.asc(), auction.auctionId.asc())
        .limit(limit.max())
        .fetch();
  }

  @Override
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

  @Override
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

  @Override
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

  @Override
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

  @Override
  public List<Bid> findAllBidsWithMemberByIdIn(List<Long> bidIds) {
    return queryFactory
        .selectFrom(bid)
        .join(bid.member, member)
        .fetchJoin()
        .where(bid.bidId.in(bidIds))
        .fetch();
  }
}
