package com.ootd.pickup.auth.token;

import java.time.Instant;

public record AccessToken(
    String value,
    Instant expiresAt
) {
}
