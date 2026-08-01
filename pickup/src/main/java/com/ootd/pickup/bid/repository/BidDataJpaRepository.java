package com.ootd.pickup.bid.repository;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.bid.domain.QBid.bid;
import static com.ootd.pickup.cards.domain.QCard.card;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.domain.QBid;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
  public List<Bid> findLastBidsByMemberId(Long memberId, Long cursorBidId, int limit) {
    QBid subBid = new QBid("subBid");
    BooleanExpression isLastBidForAuction =
        bid.bidId.eq(
            JPAExpressions.select(subBid.bidId.max())
                .from(subBid)
                .where(
                    subBid.auction.auctionId.eq(bid.auction.auctionId),
                    subBid.member.memberId.eq(memberId)));

    return queryFactory
        .selectFrom(bid)
        .join(bid.auction, auction)
        .fetchJoin()
        .join(auction.consignment, consignment)
        .fetchJoin()
        .join(consignment.card, card)
        .fetchJoin()
        .where(bid.member.memberId.eq(memberId), isLastBidForAuction, cursorPredicate(cursorBidId))
        .orderBy(bid.bidId.desc())
        .limit(limit)
        .fetch();
  }

  @Override
  public Map<Long, Long> findCurrentPricesByAuctionIds(List<Long> auctionIds) {
    if (auctionIds.isEmpty()) {
      return Map.of();
    }

    return queryFactory
        .selectFrom(bid)
        .where(bid.auction.auctionId.in(auctionIds), bid.bidStatus.eq(BidStatus.HIGHEST))
        .fetch()
        .stream()
        .collect(Collectors.toMap(highestBid -> highestBid.getAuction().getAuctionId(), Bid::getBidPrice));
  }

  private BooleanExpression cursorPredicate(Long cursorBidId) {
    return cursorBidId == null ? null : bid.bidId.lt(cursorBidId);
  }
}
