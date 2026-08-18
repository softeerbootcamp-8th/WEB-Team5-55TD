package com.ootd.pickup.point.service;

import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointLockServiceTest {

  @Mock private PointRepository pointRepository;

  private PointLockService pointLockService;

  @BeforeEach
  void setUp() {
    pointLockService = new PointLockService(pointRepository);
  }

  @Test
  void 존재하지_않는_회원의_포인트를_조회하면_예외가_발생한다() {
    // given
    given(pointRepository.findByMemberIdForUpdate(1L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> pointLockService.getPointForUpdate(1L))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(POINT_NOT_FOUND.getClientExceptionCode().name()));
  }

  @Test
  void 여러_회원의_포인트를_memberId_오름차순으로_잠근다() {
    // given
    Point point2 = createPoint(2L);
    Point point5 = createPoint(5L);
    given(pointRepository.findByMemberIdForUpdate(2L)).willReturn(Optional.of(point2));
    given(pointRepository.findByMemberIdForUpdate(5L)).willReturn(Optional.of(point5));
    List<Long> memberIds = Arrays.asList(5L, 2L);

    // when
    Map<Long, Point> points = pointLockService.lockPoints(memberIds);

    // then
    assertThat(points).containsOnlyKeys(2L, 5L);
    InOrder inOrder = Mockito.inOrder(pointRepository);
    inOrder.verify(pointRepository).findByMemberIdForUpdate(2L);
    inOrder.verify(pointRepository).findByMemberIdForUpdate(5L);
  }

  @Test
  void null과_중복된_memberId는_무시하고_잠근다() {
    // given
    Point point = createPoint(3L);
    given(pointRepository.findByMemberIdForUpdate(3L)).willReturn(Optional.of(point));
    List<Long> memberIds = Arrays.asList(3L, null, 3L);

    // when
    Map<Long, Point> points = pointLockService.lockPoints(memberIds);

    // then
    assertThat(points).containsOnlyKeys(3L);
    then(pointRepository).should(times(1)).findByMemberIdForUpdate(3L);
  }

  private Point createPoint(Long memberId) {
    return Point.create(memberId);
  }
}
