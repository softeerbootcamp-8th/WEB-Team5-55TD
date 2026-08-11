package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentJpaRepository extends JpaRepository<Consignment, Long> {

  boolean existsBySellerMember_MemberIdAndStatus(
      Long sellerMemberId, ConsignmentStatus consignmentStatus);
}
