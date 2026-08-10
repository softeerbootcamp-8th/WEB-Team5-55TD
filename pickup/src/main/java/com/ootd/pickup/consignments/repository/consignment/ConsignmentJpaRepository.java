package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentJpaRepository extends JpaRepository<Consignment, Long> {

  boolean existsBySellerMember_MemberIdAndStatusIn(
      Long sellerMemberId, List<ConsignmentStatus> statuses);
}
