package com.ootd.pickup.consignments.dto.request;

import com.ootd.pickup.consignments.domain.CardState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ModifyConsignmentRequest(
    @NotNull CardState cardState,
    @Size(max = 255, message = "주요 결함은 255자 이하여야 합니다.") String majorDefect,
    @NotNull @Valid CertificateRequest certificate,
    @NotNull @Size(min = 2, max = 5) @Valid List<ConsignmentImageRequest> images) {}
