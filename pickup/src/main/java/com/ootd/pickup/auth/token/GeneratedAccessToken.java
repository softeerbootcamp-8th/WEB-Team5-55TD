package com.ootd.pickup.auth.token;

import java.time.Instant;

public record GeneratedAccessToken(
    String value,
    String tokenId,
    String sessionId,
    Instant expiresAt
) {
}
