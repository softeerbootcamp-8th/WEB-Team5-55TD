package com.ootd.pickup.point.repository;

import static com.ootd.pickup.point.domain.QPointReservation.pointReservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ootd.pickup.point.domain.PointReservation;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointReservationDataJpaRepositoryTest {

  @Mock private PointReservationJpaRepository pointReservationJpaRepository;
  @Mock private JPAQueryFactory queryFactory;
  @Mock private JPAQuery<Long> reservationIdQuery;
  @Mock private JPAQuery<PointReservation> reservationQuery;

  private PointReservationDataJpaRepository pointReservationRepository;

  @BeforeEach
  void setUp() {
    pointReservationRepository =
        new PointReservationDataJpaRepository(pointReservationJpaRepository, queryFactory);
  }

  @Test
  void 예약이_없으면_예약식별자만_조회하고_쓰기락을_획득하지_않는다() {
    // given
    given(queryFactory.select(pointReservation.pointReservationId)).willReturn(reservationIdQuery);
    given(reservationIdQuery.from(pointReservation)).willReturn(reservationIdQuery);
    given(reservationIdQuery.where(pointReservation.auction.auctionId.eq(1L)))
        .willReturn(reservationIdQuery);
    given(reservationIdQuery.fetchOne()).willReturn(null);

    // when
    Optional<PointReservation> reservation =
        pointReservationRepository.findByAuctionIdForUpdate(1L);

    // then
    assertThat(reservation).isEmpty();
    then(queryFactory).should(never()).selectFrom(pointReservation);
  }

  @Test
  void 예약이_있으면_식별자를_조회한_뒤_해당예약에_쓰기락을_획득한다() {
    // given
    PointReservation reservation = org.mockito.Mockito.mock(PointReservation.class);
    given(queryFactory.select(pointReservation.pointReservationId)).willReturn(reservationIdQuery);
    given(reservationIdQuery.from(pointReservation)).willReturn(reservationIdQuery);
    given(reservationIdQuery.where(pointReservation.auction.auctionId.eq(1L)))
        .willReturn(reservationIdQuery);
    given(reservationIdQuery.fetchOne()).willReturn(10L);
    given(queryFactory.selectFrom(pointReservation)).willReturn(reservationQuery);
    given(reservationQuery.where(pointReservation.pointReservationId.eq(10L)))
        .willReturn(reservationQuery);
    given(reservationQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE))
        .willReturn(reservationQuery);
    given(reservationQuery.fetchOne()).willReturn(reservation);

    // when
    Optional<PointReservation> found = pointReservationRepository.findByAuctionIdForUpdate(1L);

    // then
    assertThat(found).containsSame(reservation);
    then(reservationQuery).should().setLockMode(LockModeType.PESSIMISTIC_WRITE);
  }
}
