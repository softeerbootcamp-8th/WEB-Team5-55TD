package com.ootd.pickup.consignments.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ModifyConsignmentRequest(
    String cardState,
    String majorDefect,
    @NotNull @Valid CertificateRequest certificate,
    @NotNull @Size(min = 2, max = 5) @Valid List<ConsignmentImageRequest> images) {}
