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
import com.querydsl.core.types.Predicate;
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
import java.util.ArrayList;
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
  public boolean updateWinningBidAndExtendEndAtIfClosingSoon(
      Auction targetAuction, Long newWinningBidId, Long newWinningPrice, LocalDateTime bidAt) {
    LocalDateTime currentEndAt = targetAuction.getEndedAt();
    LocalDateTime softCloseBoundary =
        currentEndAt == null ? null : bidAt.plus(AuctionSchedulePolicy.SOFT_CLOSE_WINDOW);
    boolean closingSoon =
        currentEndAt != null
            && currentEndAt.isAfter(bidAt)
            && currentEndAt.isBefore(softCloseBoundary);

    JPAUpdateClause update =
        new JPAUpdateClause(entityManager, auction)
            .set(auction.winningBidId, newWinningBidId)
            .set(auction.winningPrice, newWinningPrice);

    List<Predicate> conditions =
        new ArrayList<>(
            List.of(
                auction.auctionId.eq(targetAuction.getAuctionId()),
                auction.auctionStatus.eq(AuctionStatus.ONGOING)));

    LocalDateTime extendedEndAt = null;
    if (closingSoon) {
      extendedEndAt = currentEndAt.plus(AuctionSchedulePolicy.SOFT_CLOSE_WINDOW);
      update.set(auction.endedAt, extendedEndAt);
      conditions.add(auction.endedAt.eq(currentEndAt));
      conditions.add(auction.endedAt.gt(bidAt));
      conditions.add(auction.endedAt.lt(softCloseBoundary));
    }

    long updatedRows = update.where(conditions.toArray(new Predicate[0])).execute();
    boolean updated = updatedRows == 1;
    boolean extended = closingSoon && updated;

    // 벌크 UPDATE는 대상 테이블(auction)과 겹치는 엔티티만 자동 플러시 대상으로 본다. 같은 트랜잭션에서
    // 앞서 변경된 Point/PointReservation/Bid 같은 다른 테이블의 미반영 변경은 이 벌크 UPDATE로는 플러시되지
    // 않으므로, entityManager.clear()로 영속성 컨텍스트를 비우기 전에 명시적으로 flush해 두지 않으면 그 변경이
    // 조용히 유실된다(clear()는 미반영 변경을 DB에 반영하지 않고 그냥 버린다).
    entityManager.flush();

    // 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 이후 조회가 갱신 전 값을 보지 않게 한다. 호출자가 넘긴
    // targetAuction은 findByIdForUpdate로 이미 행 락을 잡고 있어, 이 메서드가 실행되는 동안 동시 변경이
    // 불가능하다는 전제 하에 WHERE 절의 auctionId·status 조건은 항상 매치된다고 본다 — 그럼에도 updated로
    // 한 번 더 확인해, 실제로 DB 행이 바뀌지 않았는데 메모리상의 엔티티만 앞서가는 상황을 만들지 않는다.
    entityManager.clear();
    if (updated) {
      targetAuction.updateWinningBid(newWinningBidId, newWinningPrice);
      if (extended) {
        targetAuction.extendEndAtBySoftCloseWindow();
      }
    }
    return extended;
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
