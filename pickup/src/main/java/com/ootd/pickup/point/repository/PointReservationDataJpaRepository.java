package com.ootd.pickup.point.repository;

import static com.ootd.pickup.point.domain.QPointReservation.pointReservation;

import com.ootd.pickup.point.domain.PointReservation;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointReservationDataJpaRepository implements PointReservationRepository {

  private final PointReservationJpaRepository pointReservationJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Optional<PointReservation> findByAuctionIdForUpdate(Long auctionId) {
    Long reservationId =
        queryFactory
            .select(pointReservation.pointReservationId)
            .from(pointReservation)
            .where(pointReservation.auction.auctionId.eq(auctionId))
            .fetchOne();
    if (reservationId == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(
        ((JPAQuery<PointReservation>)
                queryFactory
                    .selectFrom(pointReservation)
                    .where(pointReservation.pointReservationId.eq(reservationId)))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  @Override
  public PointReservation save(PointReservation reservation) {
    return pointReservationJpaRepository.save(reservation);
  }
}
