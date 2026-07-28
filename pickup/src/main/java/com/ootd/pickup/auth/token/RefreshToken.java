package com.ootd.pickup.auth.token;

public record RefreshToken(
    String value,
    String hash
) {
}
