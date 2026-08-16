package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointReservation;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointReservationDataJpaRepository implements PointReservationRepository {

  private final PointReservationJpaRepository pointReservationJpaRepository;

  @Override
  public Optional<PointReservation> findByAuctionIdForUpdate(Long auctionId) {
    return pointReservationJpaRepository
        .findIdByAuctionId(auctionId)
        .flatMap(pointReservationJpaRepository::findByIdForUpdate);
  }

  @Override
  public PointReservation save(PointReservation reservation) {
    return pointReservationJpaRepository.save(reservation);
  }
}
