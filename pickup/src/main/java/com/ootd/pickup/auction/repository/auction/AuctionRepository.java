package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;

public interface AuctionRepository {
  Auction save(Auction auction);
}
