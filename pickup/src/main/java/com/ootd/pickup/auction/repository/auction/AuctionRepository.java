package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import java.util.Optional;

public interface AuctionRepository {
  Auction save(Auction auction);

  Optional<Auction> findByIdForUpdate(Long auctionId);
}
