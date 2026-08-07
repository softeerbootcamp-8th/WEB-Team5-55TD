package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.Point;
import java.util.List;
import java.util.Optional;

public interface PointRepository {

  Optional<Point> findByMemberId(Long memberId);

  List<Point> findAllByMemberIdIn(List<Long> memberIds);

  Optional<Point> findByMemberIdForUpdate(Long memberId);

  Point save(Point point);
}
