package com.ootd.pickup.images.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "image.storage")
public record ImageStorageProperties(
    @NotBlank String bucket,
    @NotBlank String region,
    @NotBlank String mediaBaseUrl,
    @NotNull Duration uploadUrlTtl) {

  public ImageStorageProperties {
    if (mediaBaseUrl != null && mediaBaseUrl.endsWith("/")) {
      throw new IllegalArgumentException("image.storage.media-base-url must not end with '/'");
    }
  }
}
