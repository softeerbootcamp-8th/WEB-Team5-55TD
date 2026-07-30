package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import java.time.LocalDate;

public record CertificateResponse(
    Long certificateId,
    String serialNumber,
    CertificationBody certificationBody,
    String grade,
    LocalDate inspectedAt) {

  public static CertificateResponse from(Certificate certificate) {
    return new CertificateResponse(
        certificate.getCertificateId(),
        certificate.getSerialNumber(),
        certificate.getCertificationBody(),
        String.valueOf(certificate.getGrade().getScore()),
        certificate.getInspectedAt());
  }
}
