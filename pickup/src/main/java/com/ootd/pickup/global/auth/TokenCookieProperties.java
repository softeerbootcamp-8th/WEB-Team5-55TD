package com.ootd.pickup.global.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.cookie")
public record TokenCookieProperties(
    boolean secure,
    String sameSite
) {
}
