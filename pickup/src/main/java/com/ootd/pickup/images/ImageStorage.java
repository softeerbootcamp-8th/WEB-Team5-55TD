package com.ootd.pickup.images;

import java.time.Instant;
import java.util.Map;

public interface ImageStorage {

  PresignedUpload createUploadUrl(String objectKey, String contentType);

  StoredObject getObject(String objectKey);

  byte[] readHeader(String objectKey, int lastByteIndex);

  void copyToFinalObject(
      String sourceObjectKey, String targetObjectKey, String sourceETag, String contentType);

  void deleteObject(String objectKey);

  record PresignedUpload(
      String uploadUrl, Map<String, String> requiredHeaders, Instant expiresAt) {}

  record StoredObject(long contentLength, String contentType, String eTag) {}
}
