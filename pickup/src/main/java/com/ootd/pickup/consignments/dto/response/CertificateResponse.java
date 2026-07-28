package com.ootd.pickup.consignments.dto.response;

import java.time.LocalDate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;

public record CertificateResponse(
    Long certificateId,
    String serialNumber,
    CertificationBody certificationBody,
    String grade,
    String gradeCode,
    LocalDate inspectedAt
) {
    public static CertificateResponse from(Certificate certificate) {
        return new CertificateResponse(
            certificate.getCertificateId(),
            certificate.getSerialNumber(),
            certificate.getCertificationBody(),
            String.valueOf(certificate.getGrade().getScore()),
            certificate.getGrade().name(),
            certificate.getInspectedAt()
        );
    }
}
