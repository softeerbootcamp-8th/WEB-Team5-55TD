package com.ootd.pickup.point.repository;

import static com.ootd.pickup.point.domain.QPoint.point;

import com.ootd.pickup.point.domain.Point;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointDataJpaRepository implements PointRepository {

  private final PointJpaRepository pointJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Optional<Point> findByMemberId(Long memberId) {
    return pointJpaRepository.findByMemberId(memberId);
  }

  @Override
  public Optional<Point> findByMemberIdForUpdate(Long memberId) {
    return Optional.ofNullable(
        ((JPAQuery<Point>) queryFactory.selectFrom(point).where(point.memberId.eq(memberId)))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  @Override
  public Point save(Point p) {
    return pointJpaRepository.save(p);
  }
}
