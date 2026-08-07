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

  private static final Duration MAX_UPLOAD_URL_TTL = Duration.ofDays(7);

  public ImageStorageProperties {
    if (mediaBaseUrl != null && mediaBaseUrl.endsWith("/")) {
      throw new IllegalArgumentException("image.storage.media-base-url must not end with '/'");
    }
    if (uploadUrlTtl != null
        && (uploadUrlTtl.isZero()
            || uploadUrlTtl.isNegative()
            || uploadUrlTtl.compareTo(MAX_UPLOAD_URL_TTL) > 0)) {
      throw new IllegalArgumentException(
          "image.storage.upload-url-ttl must be greater than zero and at most 7 days");
    }
  }
}
