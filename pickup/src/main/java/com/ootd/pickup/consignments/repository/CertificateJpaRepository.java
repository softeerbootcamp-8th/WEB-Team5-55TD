package com.ootd.pickup.consignments.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;

public interface CertificateJpaRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByConsignment(Consignment consignment);
}
