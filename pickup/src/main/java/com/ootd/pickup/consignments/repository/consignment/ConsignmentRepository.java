package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConsignmentRepository {
  Consignment save(Consignment consignment);

  Optional<Consignment> findConsignmentById(Long consignmentId);

  Optional<Consignment> findByIdForUpdate(Long consignmentId);

  void deleteById(Long consignmentId);

  List<Consignment> findAllBySellerMemberIdAndStatusAndCursor(
      Long sellerMemberId, ConsignmentStatus status, Long cursor, int size);

  Page<Consignment> searchConsignmentsForAdmin(
      String q, List<ConsignmentStatus> statuses, Long sellerMemberId, Pageable pageable);
}
