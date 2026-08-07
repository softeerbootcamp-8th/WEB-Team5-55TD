package com.ootd.pickup.images.service;

import com.ootd.pickup.images.config.ImageStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageUrlResolver {

  private final ImageStorageProperties properties;

  public String resolve(String objectKey) {
    return objectKey == null ? null : properties.mediaBaseUrl() + "/" + objectKey;
  }
}
