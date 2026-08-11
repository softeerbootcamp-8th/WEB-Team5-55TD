package com.ootd.pickup.auth.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.kakao")
public record KakaoProperties(String clientId, String clientSecret) {}
