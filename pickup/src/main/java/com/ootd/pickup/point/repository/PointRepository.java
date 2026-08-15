package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.Point;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PointRepository {

  Optional<Point> findByMemberId(Long memberId);

  Optional<Point> findByMemberIdForUpdate(Long memberId);

  List<Point> findAllByMemberIdInForUpdate(Collection<Long> memberIds);

  Point save(Point point);
}
