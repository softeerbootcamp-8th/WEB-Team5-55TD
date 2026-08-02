package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsignmentJpaRepository extends JpaRepository<Consignment, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Consignment c where c.consignmentId = :consignmentId")
  Optional<Consignment> findByIdForUpdate(@Param("consignmentId") Long consignmentId);

  long countBySellerMember_MemberIdAndStatus(Long sellerMemberId, ConsignmentStatus status);
}
