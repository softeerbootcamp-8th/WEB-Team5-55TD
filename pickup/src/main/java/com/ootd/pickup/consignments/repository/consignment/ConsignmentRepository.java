package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.consignments.domain.Consignment;
import java.util.Optional;

public interface ConsignmentRepository {
  Consignment save(Consignment consignment);

  Optional<Consignment> findConsignmentById(Long consignmentId);

  Optional<Consignment> findByIdForUpdate(Long consignmentId);

  void deleteById(Long consignmentId);
}
