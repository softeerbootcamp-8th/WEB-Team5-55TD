package com.ootd.pickup.auth.token;

public interface AccessTokenGenerator {
    GeneratedAccessToken generate(Long memberId, String sessionId);
}
