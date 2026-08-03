package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BidRepository {

  Bid save(Bid bid);

  Optional<Bid> findFirstByAuctionIdAndBidStatus(Long auctionId, BidStatus bidStatus);

  List<Bid> findLastBidsByMemberId(Long memberId, Long cursorBidId, int limit);

  List<Bid> findWonBidsByMemberId(Long memberId, Long cursorBidId, int limit);

  Map<Long, Long> findCurrentPricesByAuctionIds(List<Long> auctionIds);

  List<Bid> findAllByAuctionId(Long auctionId, Long cursorBidId, int limit);
}
