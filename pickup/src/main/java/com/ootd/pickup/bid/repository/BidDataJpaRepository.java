package com.ootd.pickup.bid.repository;

import static com.ootd.pickup.bid.domain.QBid.bid;
import static com.ootd.pickup.member.domain.QMember.member;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BidDataJpaRepository implements BidRepository {

  private final BidJpaRepository bidJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Bid save(Bid bid) {
    return bidJpaRepository.save(bid);
  }

  @Override
  public Optional<Bid> findFirstByAuctionIdAndBidStatus(Long auctionId, BidStatus bidStatus) {
    return bidJpaRepository.findFirstByAuctionAuctionIdAndBidStatusOrderByBidPriceDesc(
        auctionId, bidStatus);
  }

  @Override
  public List<Bid> findAllByAuctionId(Long auctionId, Long cursorBidId, int limit) {
    return queryFactory
        .selectFrom(bid)
        .join(bid.member, member)
        .fetchJoin()
        .where(bid.auction.auctionId.eq(auctionId), cursorPredicate(cursorBidId))
        .orderBy(bid.bidId.desc())
        .limit(limit)
        .fetch();
  }

  private BooleanExpression cursorPredicate(Long cursorBidId) {
    return cursorBidId == null ? null : bid.bidId.lt(cursorBidId);
  }
}
