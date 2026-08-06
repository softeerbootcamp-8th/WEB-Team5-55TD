package com.ootd.pickup.images.dto;

import com.ootd.pickup.images.domain.ImagePurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateImageUploadRequest(
    @NotNull ImagePurpose purpose,
    @NotBlank String contentType,
    @Min(1) @Max(10 * 1024 * 1024) long contentLength) {}
