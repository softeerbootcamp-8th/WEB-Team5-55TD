package com.ootd.pickup.auction.cache;

import java.util.Optional;

/**
 * 경매 스냅샷 캐시 접근 계약.
 *
 * <p>구현체는 조회·저장 실패를 호출자에게 전파하지 않는다. 이 캐시는 어디까지나 밑져야 본전인 사전 필터용이라, 캐시 자체의 장애가 입찰 요청 생성 흐름을 막아서는 안
 * 된다.
 */
public interface AuctionSnapshotCache {

  Optional<AuctionSnapshot> find(Long auctionId);

  void put(AuctionSnapshot snapshot);
}
