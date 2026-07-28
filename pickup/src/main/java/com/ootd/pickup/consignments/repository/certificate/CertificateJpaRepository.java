package com.ootd.pickup.consignments.repository.certificate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateJpaRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByConsignment(Consignment consignment);
}
