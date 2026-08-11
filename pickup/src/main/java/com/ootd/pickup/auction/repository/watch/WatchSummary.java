package com.ootd.pickup.auction.repository.watch;

/**
 * 경매 하나에 대한 관심 집계.
 *
 * <p>목록·상세 조회는 전체 관심 수와 조회자 본인의 관심 여부를 항상 함께 쓴다. 두 값을 한 번의 쿼리로 묶어, 같은 트랜잭션 안에서 DB 왕복을 하나 줄인다.
 */
public record WatchSummary(long count, boolean watchedByViewer) {

  public static final WatchSummary EMPTY = new WatchSummary(0L, false);
}
