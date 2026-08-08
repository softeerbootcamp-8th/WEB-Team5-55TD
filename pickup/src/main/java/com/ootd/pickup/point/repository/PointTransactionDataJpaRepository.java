package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointTransaction;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointTransactionDataJpaRepository implements PointTransactionRepository {

  private final PointTransactionJpaRepository pointTransactionJpaRepository;

  @Override
  public PointTransaction save(PointTransaction transaction) {
    return pointTransactionJpaRepository.save(transaction);
  }

  @Override
  public List<PointTransaction> findAllByMemberId(Long memberId, Long cursorId, int limit) {
    return pointTransactionJpaRepository.findAllByMemberId(
        memberId, cursorId, PageRequest.of(0, limit));
  }
}
