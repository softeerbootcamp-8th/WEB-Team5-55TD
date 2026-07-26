package com.ootd.pickup.consignments.dto.request;

import java.time.LocalDate;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.Grade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CertificateRequest(
        @NotBlank String serialNumber,
        @NotBlank String certificationBody,
        @NotBlank String grade,
        @NotNull LocalDate inspectedAt
) {
    public Certificate toEntity(Consignment consignment) {
        return Certificate.builder()
                .consignment(consignment)
                .serialNumber(serialNumber)
                .certificationBody(CertificationBody.from(certificationBody))
                .grade(Grade.from(grade))
                .inspectedAt(inspectedAt)
                .build();
    }
}
