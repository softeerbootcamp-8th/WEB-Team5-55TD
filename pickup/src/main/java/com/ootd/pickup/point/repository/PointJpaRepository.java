package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.Point;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointJpaRepository extends JpaRepository<Point, Long> {

  Optional<Point> findByMemberId(Long memberId);
}
