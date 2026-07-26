package com.ootd.pickup.consignments.repository;

import java.util.Optional;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;

public interface CertificateRepository {
    Certificate save(Certificate certificate);

    Optional<Certificate> findCertificateByConsignment(Consignment consignment);
}
