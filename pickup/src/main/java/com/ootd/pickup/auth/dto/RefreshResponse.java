package com.ootd.pickup.auth.dto;

import java.time.Instant;

public record RefreshResponse(Instant expiresAt) {
}
