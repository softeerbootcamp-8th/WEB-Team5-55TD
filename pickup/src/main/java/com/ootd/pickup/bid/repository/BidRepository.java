package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import java.util.List;
import java.util.Optional;

public interface BidRepository {

  Bid save(Bid bid);

  Optional<Bid> findFirstByAuctionIdAndBidStatus(Long auctionId, BidStatus bidStatus);

  List<Bid> findAllByAuctionId(Long auctionId, Long cursorBidId, int limit);
}
