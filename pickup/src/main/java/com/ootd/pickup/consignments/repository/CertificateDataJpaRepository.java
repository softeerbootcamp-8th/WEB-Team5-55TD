package com.ootd.pickup.consignments.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CertificateDataJpaRepository implements CertificateRepository {

    private final CertificateJpaRepository certificateJpaRepository;

    @Override
    public Certificate save(Certificate certificate) {
        return certificateJpaRepository.save(certificate);
    }

    @Override
    public Optional<Certificate> findCertificateByConsignment(Consignment consignment) {
        return certificateJpaRepository.findByConsignment(consignment);
    }
}
