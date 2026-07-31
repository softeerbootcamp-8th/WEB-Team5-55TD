package com.ootd.pickup.consignments.repository.certificate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository {
  Certificate save(Certificate certificate);

  Optional<Certificate> findCertificateByConsignment(Consignment consignment);

  List<Certificate> findAllByConsignmentIn(List<Consignment> consignments);

  void deleteByConsignment(Consignment consignment);

  void flush();
}
