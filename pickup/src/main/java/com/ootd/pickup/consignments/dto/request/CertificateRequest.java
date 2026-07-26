package com.ootd.pickup.consignments.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CertificateRequest(
        @NotBlank String serialNumber,
        @NotBlank String certificationBody,
        @NotBlank String grade,
        @NotNull LocalDate inspectedAt
) {
}
