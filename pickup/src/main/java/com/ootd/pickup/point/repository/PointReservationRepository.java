package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointReservation;
import java.util.Optional;

public interface PointReservationRepository {

  /**
   * 경매의 기존 포인트 예약을 잠근다.
   *
   * <p>구현체는 {@code auctionId}로 예약 식별자를 일반 조회한 뒤, 예약이 존재할 때만 기본 키로 쓰기 락을 획득한다. 예약이 없는 첫 입찰에서는 {@code
   * auction_id} 유니크 인덱스의 갭을 잠그지 않는다.
   */
  Optional<PointReservation> findByAuctionIdForUpdate(Long auctionId);

  PointReservation save(PointReservation reservation);
}
