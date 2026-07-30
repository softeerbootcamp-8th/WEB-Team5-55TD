package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.util.List;

public interface AuctionRepository {
  Auction save(Auction auction);

  List<Auction> searchAuctions(
      String q, List<AuctionStatus> statuses, AuctionSort sort, AuctionCursor cursor, int limit);
}
