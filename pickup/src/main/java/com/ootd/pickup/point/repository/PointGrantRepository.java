package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointGrant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PointGrantRepository {

  PointGrant save(PointGrant pointGrant);

  Page<PointGrant> findAllByMemberId(Long memberId, Pageable pageable);
}
