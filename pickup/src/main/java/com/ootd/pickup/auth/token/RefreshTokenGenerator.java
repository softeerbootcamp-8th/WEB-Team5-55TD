package com.ootd.pickup.auth.token;

public interface RefreshTokenGenerator {
    GeneratedRefreshToken generate();

    String hash(String refreshToken);
}
