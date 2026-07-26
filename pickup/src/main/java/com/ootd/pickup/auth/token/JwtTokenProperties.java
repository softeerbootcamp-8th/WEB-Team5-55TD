package com.ootd.pickup.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.token")
public record JwtTokenProperties(
        String issuer,
        String secret,
        Duration accessTokenTtl
) {
}
