package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import java.util.Optional;

public interface ConsignmentRepository {
  Consignment save(Consignment consignment);

  Optional<Consignment> findConsignmentById(Long consignmentId);

  Optional<Consignment> findByIdForUpdate(Long consignmentId);

  void deleteById(Long consignmentId);

  long countBySellerMemberIdAndStatus(Long sellerMemberId, ConsignmentStatus status);
}
