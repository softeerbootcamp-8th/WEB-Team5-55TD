package com.ootd.pickup.auth.token.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.token")
public record JwtTokenProperties(
    String issuer,
    String secret,
    Duration accessTokenTtl,
    Duration refreshTokenTtl
) {
}
