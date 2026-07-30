package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.consignments.domain.Consignment;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConsignmentDataJpaRepository implements ConsignmentRepository {

  private final ConsignmentJpaRepository consignmentJpaRepository;

  @Override
  public Consignment save(Consignment consignment) {
    return consignmentJpaRepository.save(consignment);
  }

  @Override
  public Optional<Consignment> findConsignmentById(Long consignmentId) {
    return consignmentJpaRepository.findById(consignmentId);
  }

  @Override
  public Optional<Consignment> findByIdForUpdate(Long consignmentId) {
    return consignmentJpaRepository.findByIdForUpdate(consignmentId);
  }
}
