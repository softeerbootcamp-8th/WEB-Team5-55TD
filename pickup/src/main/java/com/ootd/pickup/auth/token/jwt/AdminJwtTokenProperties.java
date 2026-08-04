package com.ootd.pickup.auth.token.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.admin-token")
public record AdminJwtTokenProperties(Duration accessTokenTtl) {}
