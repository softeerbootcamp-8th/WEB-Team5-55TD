package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.Bid;
import java.util.List;
import java.util.Optional;

public interface BidRepository {

  Bid save(Bid bid);

  Optional<Bid> findById(Long bidId);

  List<Bid> findLastBidsByMemberId(Long memberId, Long cursorBidId, int limit);

  List<Bid> findWonBidsByMemberId(Long memberId, Long cursorBidId, int limit);

  List<Bid> findAllByAuctionId(Long auctionId, Long cursorBidId, int limit);
}
