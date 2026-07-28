package com.ootd.pickup.consignments.repository.certificate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;

import java.util.Optional;

public interface CertificateRepository {
    Certificate save(Certificate certificate);

    Optional<Certificate> findCertificateByConsignment(Consignment consignment);
}
