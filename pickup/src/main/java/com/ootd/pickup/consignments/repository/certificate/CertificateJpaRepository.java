package com.ootd.pickup.consignments.repository.certificate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateJpaRepository extends JpaRepository<Certificate, Long> {
  Optional<Certificate> findByConsignment(Consignment consignment);

  void deleteByConsignment(Consignment consignment);
}
