package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.Point;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointDataJpaRepository implements PointRepository {

  private final PointJpaRepository pointJpaRepository;

  @Override
  public Optional<Point> findByMemberId(Long memberId) {
    return pointJpaRepository.findByMemberId(memberId);
  }

  @Override
  public Point save(Point point) {
    return pointJpaRepository.save(point);
  }
}
