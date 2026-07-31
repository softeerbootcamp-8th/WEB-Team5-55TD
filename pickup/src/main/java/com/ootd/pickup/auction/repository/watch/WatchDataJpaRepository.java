package com.ootd.pickup.auction.repository.watch;

import static com.ootd.pickup.auction.domain.QWatch.*;

import com.querydsl.core.Tuple;
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

  private final JPAQueryFactory queryFactory;

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
}
