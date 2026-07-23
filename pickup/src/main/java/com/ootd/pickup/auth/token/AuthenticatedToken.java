package com.ootd.pickup.auth.token;

import java.time.Instant;

public record AuthenticatedToken(
        Long memberId,
        String sessionId,
        String tokenId,
        Instant expiresAt
) {
}
