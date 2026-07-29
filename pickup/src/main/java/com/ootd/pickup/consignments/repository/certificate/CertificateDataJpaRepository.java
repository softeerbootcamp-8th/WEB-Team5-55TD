package com.ootd.pickup.consignments.repository.certificate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
