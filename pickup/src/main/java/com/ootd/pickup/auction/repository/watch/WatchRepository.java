package com.ootd.pickup.auction.repository.watch;

import com.ootd.pickup.auction.domain.Watch;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WatchRepository {
  Watch save(Watch watch);

  void flush();

  int deleteByMemberIdAndAuctionId(Long memberId, Long auctionId);

  int deleteByAuctionId(Long auctionId);

  Map<Long, Long> countByAuctionIds(List<Long> auctionIds);

  Set<Long> findWatchedAuctionIds(Long memberId, List<Long> auctionIds);

  /**
   * 경매별 관심 수와 조회자 본인의 관심 여부를 한 번의 쿼리로 함께 구한다.
   *
   * @param viewerMemberId 조회자 식별자. 비로그인 조회는 {@code null}이며 이때 {@code watchedByViewer}는 항상 {@code
   *     false}다
   * @param auctionIds 조회할 경매 식별자 목록
   * @return 관심이 하나도 없는 경매는 결과에서 빠진다. 호출부는 {@link WatchSummary#EMPTY}로 기본값을 처리한다
   */
  Map<Long, WatchSummary> findWatchSummariesByAuctionIds(
      Long viewerMemberId, List<Long> auctionIds);

  List<Watch> findAllActiveByMemberId(Long memberId, Long cursorWatchId, int limit);
}
