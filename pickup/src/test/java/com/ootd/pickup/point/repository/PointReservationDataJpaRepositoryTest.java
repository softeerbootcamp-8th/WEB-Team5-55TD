package com.ootd.pickup.point.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ootd.pickup.point.domain.PointReservation;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointReservationDataJpaRepositoryTest {

  @Mock private PointReservationJpaRepository pointReservationJpaRepository;

  private PointReservationDataJpaRepository pointReservationRepository;

  @BeforeEach
  void setUp() {
    pointReservationRepository =
        new PointReservationDataJpaRepository(pointReservationJpaRepository);
  }

  @Test
  void 예약이_없으면_예약식별자만_조회하고_쓰기락을_획득하지_않는다() {
    // given
    given(pointReservationJpaRepository.findIdByAuctionId(1L)).willReturn(Optional.empty());

    // when
    Optional<PointReservation> reservation =
        pointReservationRepository.findByAuctionIdForUpdate(1L);

    // then
    assertThat(reservation).isEmpty();
    then(pointReservationJpaRepository).should(never()).findByIdForUpdate(anyLong());
  }

  @Test
  void 예약이_있으면_식별자를_조회한_뒤_해당예약에_쓰기락을_획득한다() {
    // given
    PointReservation reservation = org.mockito.Mockito.mock(PointReservation.class);
    given(pointReservationJpaRepository.findIdByAuctionId(1L)).willReturn(Optional.of(10L));
    given(pointReservationJpaRepository.findByIdForUpdate(10L))
        .willReturn(Optional.of(reservation));

    // when
    Optional<PointReservation> found = pointReservationRepository.findByAuctionIdForUpdate(1L);

    // then
    assertThat(found).containsSame(reservation);
    then(pointReservationJpaRepository).should().findByIdForUpdate(10L);
  }
}
