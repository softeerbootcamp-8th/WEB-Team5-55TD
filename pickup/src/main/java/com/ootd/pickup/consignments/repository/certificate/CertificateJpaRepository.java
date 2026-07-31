package com.ootd.pickup.consignments.repository.certificate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateJpaRepository extends JpaRepository<Certificate, Long> {
  Optional<Certificate> findByConsignment(Consignment consignment);

  List<Certificate> findAllByConsignmentIn(List<Consignment> consignments);

  void deleteByConsignment(Consignment consignment);

  List<Certificate> findAllByConsignment_ConsignmentIdIn(List<Long> consignmentIds);
}
