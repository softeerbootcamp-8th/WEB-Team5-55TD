package com.ootd.pickup.cards.sync.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tcgdex")
public record TcgdexProperties(String baseUrl, Duration timeout) {
  public TcgdexProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api.tcgdex.net/v2/en";
    }
    if (timeout == null) {
      timeout = Duration.ofSeconds(10);
    }
  }
}
