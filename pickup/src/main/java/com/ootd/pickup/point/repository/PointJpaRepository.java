package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.Point;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointJpaRepository extends JpaRepository<Point, Long> {

  Optional<Point> findByMemberId(Long memberId);

  List<Point> findAllByMemberIdIn(List<Long> memberIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Point p where p.memberId = :memberId")
  Optional<Point> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
