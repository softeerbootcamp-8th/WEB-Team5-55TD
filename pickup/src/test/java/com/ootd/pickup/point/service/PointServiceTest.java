package com.ootd.pickup.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

  @Mock private PointRepository pointRepository;

  @InjectMocks private PointService pointService;

  @Test
  void 회원_id로_초기화하면_잔액0인_포인트_계좌가_저장된다() {
    // when
    pointService.initializePoint(1L);

    // then
    ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
    then(pointRepository).should().save(pointCaptor.capture());
    assertThat(pointCaptor.getValue().getMemberId()).isEqualTo(1L);
    assertThat(pointCaptor.getValue().getBalance()).isZero();
  }
}
