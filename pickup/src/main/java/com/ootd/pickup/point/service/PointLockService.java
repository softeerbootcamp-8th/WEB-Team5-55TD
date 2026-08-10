package com.ootd.pickup.point.service;

import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 여러 회원의 포인트를 동시에 잠글 때 memberId 오름차순으로 잠그도록 강제한다.
 *
 * <p>서로 다른 흐름(입찰 예약, 경매 정산)이 겹치는 회원 조합의 포인트를 동시에 잠글 수 있어, 락 순서가 흐름마다 다르면 교착상태가 날 수 있다. 이를 막기 위해 락
 * 순서 정책을 이 클래스 하나로 모은다.
 */
@Service
@RequiredArgsConstructor
public class PointLockService {

  private final PointRepository pointRepository;

  public Point getPointForUpdate(Long memberId) {
    return pointRepository
        .findByMemberIdForUpdate(memberId)
        .orElseThrow(() -> new PickUpException(POINT_NOT_FOUND));
  }

  public Map<Long, Point> lockPoints(Collection<Long> memberIds) {
    Map<Long, Point> points = new LinkedHashMap<>();
    memberIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .sorted(Comparator.naturalOrder())
        .forEach(memberId -> points.put(memberId, getPointForUpdate(memberId)));
    return points;
  }
}
