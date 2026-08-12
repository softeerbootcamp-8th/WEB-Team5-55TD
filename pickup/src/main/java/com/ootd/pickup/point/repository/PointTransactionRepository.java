package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointTransaction;
import java.util.List;
import java.util.Optional;

public interface PointTransactionRepository {

  PointTransaction save(PointTransaction transaction);

  List<PointTransaction> findAllByMemberId(Long memberId, Long cursorId, int limit);

  Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);
}
