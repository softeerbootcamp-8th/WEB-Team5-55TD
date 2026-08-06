package com.ootd.pickup.images.dto;

import java.time.Instant;
import java.util.Map;

public record CreateImageUploadResponse(
    String temporaryObjectKey,
    String uploadUrl,
    Map<String, String> requiredHeaders,
    Instant expiresAt) {}
