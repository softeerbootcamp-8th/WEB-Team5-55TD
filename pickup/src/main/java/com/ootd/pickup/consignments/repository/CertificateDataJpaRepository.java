package com.ootd.pickup.consignments.repository;

import org.springframework.stereotype.Repository;

import com.ootd.pickup.consignments.domain.Certificate;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CertificateDataJpaRepository implements CertificateRepository {

    private final CertificateJpaRepository certificateJpaRepository;

    @Override
    public Certificate save(Certificate certificate) {
        return certificateJpaRepository.save(certificate);
    }
}
