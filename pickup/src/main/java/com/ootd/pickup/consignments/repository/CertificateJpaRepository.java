package com.ootd.pickup.consignments.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ootd.pickup.consignments.domain.Certificate;

public interface CertificateJpaRepository extends JpaRepository<Certificate, Long> {
}
