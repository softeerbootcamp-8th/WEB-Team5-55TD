package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointTransaction;
import java.util.List;

public interface PointTransactionRepository {

  PointTransaction save(PointTransaction transaction);

  List<PointTransaction> findAllByMemberId(Long memberId, Long cursorId, int limit);
}
