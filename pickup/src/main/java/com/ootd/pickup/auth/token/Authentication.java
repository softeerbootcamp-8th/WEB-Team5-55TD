package com.ootd.pickup.auth.token;

import java.time.Instant;

public record Authentication(
        Long memberId,
        // 로그인 세션 단위 식별자
        String sessionId,
        // 발급된 액세스 토큰 한 장 단위 식별자
        String tokenId,
        Instant expiresAt
) {
}
