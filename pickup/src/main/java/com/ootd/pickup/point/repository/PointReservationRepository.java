package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointReservation;
import java.util.Optional;

public interface PointReservationRepository {

  Optional<PointReservation> findByAuctionIdForUpdate(Long auctionId);

  PointReservation save(PointReservation reservation);
}
