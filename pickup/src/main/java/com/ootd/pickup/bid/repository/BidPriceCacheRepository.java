package com.ootd.pickup.bid.repository;

import java.util.Optional;

public interface BidPriceCacheRepository {

  Optional<Long> findCurrentPrice(Long auctionId);

  void saveCurrentPrice(Long auctionId, Long currentPrice);
}
