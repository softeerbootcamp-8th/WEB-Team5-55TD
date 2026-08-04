package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointGrant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointGrantJpaRepository extends JpaRepository<PointGrant, Long> {

  Page<PointGrant> findAllByMemberId(Long memberId, Pageable pageable);
}
