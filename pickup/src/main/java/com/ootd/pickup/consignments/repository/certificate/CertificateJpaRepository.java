package com.ootd.pickup.consignments.repository.certificate;

import com.ootd.pickup.consignments.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateJpaRepository extends JpaRepository<Certificate, Long> {
}
