package com.ootd.pickup.consignments.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterConsignmentRequest(
        @NotNull Long cardId,
        // TODO: 인증 구현 이후 요청 파라미터에서 제외하고 인증 컨텍스트에서 추출하도록 변경
        @NotNull Long sellerMemberId,
        String majorDefect,
        @NotNull @Valid CertificateRequest certificate,
        @NotNull @Size(min = 2) @Valid List<ConsignmentImageRequest> images
) {
}
