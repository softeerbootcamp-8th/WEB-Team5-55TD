package com.ootd.pickup.auth.kakao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.kakao")
public record KakaoProperties(String clientId, String clientSecret, Duration timeout) {
  public KakaoProperties {
    if (timeout == null) {
      timeout = Duration.ofSeconds(5);
    }
  }
}
