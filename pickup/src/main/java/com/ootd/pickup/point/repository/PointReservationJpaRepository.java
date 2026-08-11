package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointReservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointReservationJpaRepository extends JpaRepository<PointReservation, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select reservation from PointReservation reservation where reservation.auction.auctionId = :auctionId")
  Optional<PointReservation> findByAuctionIdForUpdate(@Param("auctionId") Long auctionId);
}
