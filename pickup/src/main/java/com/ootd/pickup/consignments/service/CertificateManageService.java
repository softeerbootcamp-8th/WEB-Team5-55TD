package com.ootd.pickup.consignments.service;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateManageService {

  private final CertificateRepository certificateRepository;

  public Map<Long, Certificate> getCertificatesByConsignmentId(List<Long> consignmentIds) {
    return certificateRepository.findAllByConsignmentIds(consignmentIds).stream()
        .collect(Collectors.toMap(c -> c.getConsignment().getConsignmentId(), c -> c));
  }
}
