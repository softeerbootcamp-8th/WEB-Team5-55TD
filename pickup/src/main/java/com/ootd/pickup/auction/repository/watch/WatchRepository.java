package com.ootd.pickup.auction.repository.watch;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WatchRepository {
  Map<Long, Long> countByAuctionIds(List<Long> auctionIds);

  Set<Long> findWatchedAuctionIds(Long memberId, List<Long> auctionIds);
}
