package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointTransaction;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTransactionJpaRepository extends JpaRepository<PointTransaction, Long> {

  @Query(
      """
      select transaction
      from PointTransaction transaction
      where transaction.member.memberId = :memberId
        and (:cursorId is null or transaction.pointTransactionId < :cursorId)
      order by transaction.pointTransactionId desc
      """)
  List<PointTransaction> findAllByMemberId(
      @Param("memberId") Long memberId, @Param("cursorId") Long cursorId, Pageable pageable);
}
