package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointGrant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointGrantDataJpaRepository implements PointGrantRepository {

  private final PointGrantJpaRepository pointGrantJpaRepository;

  @Override
  public PointGrant save(PointGrant pointGrant) {
    return pointGrantJpaRepository.save(pointGrant);
  }

  @Override
  public Page<PointGrant> findAllByMemberId(Long memberId, Pageable pageable) {
    return pointGrantJpaRepository.findAllByMemberId(memberId, pageable);
  }
}
