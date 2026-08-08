package com.ootd.pickup.auction.repository.watch;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.auction.domain.QWatch.*;
import static com.ootd.pickup.cards.domain.QCard.card;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.domain.Watch;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WatchDataJpaRepository implements WatchRepository {

  private final WatchJpaRepository watchJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Watch save(Watch watch) {
    return watchJpaRepository.save(watch);
  }

  @Override
  public void flush() {
    watchJpaRepository.flush();
  }

  @Override
  public int deleteByMemberIdAndAuctionId(Long memberId, Long auctionId) {
    return (int)
        queryFactory
            .delete(watch)
            .where(watch.member.memberId.eq(memberId), watch.auction.auctionId.eq(auctionId))
            .execute();
  }

  @Override
  public Map<Long, Long> countByAuctionIds(List<Long> auctionIds) {
    if (auctionIds.isEmpty()) {
      return Map.of();
    }

    List<Tuple> rows =
        queryFactory
            .select(watch.auction.auctionId, watch.count())
            .from(watch)
            .where(watch.auction.auctionId.in(auctionIds))
            .groupBy(watch.auction.auctionId)
            .fetch();

    return rows.stream()
        .collect(
            Collectors.toMap(
                row -> row.get(watch.auction.auctionId), row -> row.get(watch.count())));
  }

  @Override
  public Set<Long> findWatchedAuctionIds(Long memberId, List<Long> auctionIds) {
    if (memberId == null || auctionIds.isEmpty()) {
      return Set.of();
    }

    return new HashSet<>(
        queryFactory
            .select(watch.auction.auctionId)
            .from(watch)
            .where(watch.member.memberId.eq(memberId), watch.auction.auctionId.in(auctionIds))
            .fetch());
  }

  @Override
  public List<Watch> findAllActiveByMemberId(Long memberId, Long cursorWatchId, int limit) {
    return queryFactory
        .selectFrom(watch)
        .join(watch.auction, auction)
        .fetchJoin()
        .join(auction.consignment, consignment)
        .fetchJoin()
        .join(consignment.card, card)
        .fetchJoin()
        .where(
            watch.member.memberId.eq(memberId),
            auction.auctionStatus.in(AuctionStatus.SCHEDULED, AuctionStatus.ONGOING),
            keysetPredicate(cursorWatchId))
        .orderBy(watch.watchId.desc())
        .limit(limit)
        .fetch();
  }

  private BooleanExpression keysetPredicate(Long cursorWatchId) {
    if (cursorWatchId == null) {
      return null;
    }
    return watch.watchId.lt(cursorWatchId);
  }
}
