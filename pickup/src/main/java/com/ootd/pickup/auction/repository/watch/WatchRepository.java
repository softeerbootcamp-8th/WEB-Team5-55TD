package com.ootd.pickup.auction.repository.watch;

import com.ootd.pickup.auction.domain.Watch;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WatchRepository {
  Watch save(Watch watch);

  void flush();

  int deleteByMemberIdAndAuctionId(Long memberId, Long auctionId);

  Map<Long, Long> countByAuctionIds(List<Long> auctionIds);

  Set<Long> findWatchedAuctionIds(Long memberId, List<Long> auctionIds);

  List<Watch> findAllActiveByMemberId(Long memberId, Long cursorWatchId, int limit);
}
