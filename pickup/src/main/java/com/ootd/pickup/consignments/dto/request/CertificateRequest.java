package com.ootd.pickup.consignments.dto.request;

import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.Grade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

public record CertificateRequest(
    @NotBlank String serialNumber,
    @NotBlank String certificationBody,
    @NotBlank String grade,
    @NotNull @PastOrPresent LocalDate inspectedAt) {
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
