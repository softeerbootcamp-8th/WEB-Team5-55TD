package com.ootd.pickup.consignments.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterConsignmentRequest(
        @NotNull Long cardId,
        String majorDefect,
        @NotNull @Valid CertificateRequest certificate,
        @NotNull @Size(min = 2) @Valid List<ConsignmentImageRequest> images
) {
}
