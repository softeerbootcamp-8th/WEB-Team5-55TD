package com.ootd.pickup.auction.repository.featured;

import java.time.Duration;
import java.util.Optional;

public interface FeaturedAuctionCacheRepository {

  Optional<Long> getFeaturedAuctionId();

  void setFeaturedAuctionId(Long auctionId, Duration ttl);

  void evictFeaturedAuctionId();
}
