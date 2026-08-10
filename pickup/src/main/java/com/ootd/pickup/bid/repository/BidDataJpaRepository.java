package com.ootd.pickup.bid.repository;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.bid.domain.QBid.bid;
import static com.ootd.pickup.cards.domain.QCard.card;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;
import static com.ootd.pickup.member.domain.QMember.member;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.QBid;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
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
  public Optional<Bid> findById(Long bidId) {
    return bidJpaRepository.findById(bidId);
  }

  @Override
  public List<Bid> findLastBidsByMemberId(Long memberId, Long cursorBidId, int limit) {
    return queryLastBidsByMemberId(memberId, cursorBidId, limit, null);
  }

  @Override
  public List<Bid> findWonBidsByMemberId(Long memberId, Long cursorBidId, int limit) {
    // Bid는 자신이 낙찰됐는지를 저장하지 않는다 — 그 Auction의 winningBidId가 이 Bid이고, 그 Auction이
    // WON으로 확정됐는지로 판단한다(Bid.getBidStatus()와 같은 근거).
    return queryLastBidsByMemberId(
        memberId,
        cursorBidId,
        limit,
        auction.winningBidId.eq(bid.bidId).and(auction.auctionStatus.eq(AuctionStatus.WON)));
  }

  private List<Bid> queryLastBidsByMemberId(
      Long memberId, Long cursorBidId, int limit, BooleanExpression additionalPredicate) {
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
        .where(
            bid.member.memberId.eq(memberId),
            isLastBidForAuction,
            additionalPredicate,
            cursorPredicate(cursorBidId))
        .orderBy(bid.bidId.desc())
        .limit(limit)
        .fetch();
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

  @Override
  public boolean existsByMemberIdAndBidStatus(Long memberId, BidStatus bidStatus) {
    return bidJpaRepository.existsByMember_MemberIdAndBidStatus(memberId, bidStatus);
  }
}
