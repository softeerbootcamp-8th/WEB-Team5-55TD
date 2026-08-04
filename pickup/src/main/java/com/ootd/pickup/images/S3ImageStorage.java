package com.ootd.pickup.images;

import static com.ootd.pickup.global.exception.ExceptionCode.IMAGE_OBJECT_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.IMAGE_STORAGE_UNAVAILABLE;
import static com.ootd.pickup.global.exception.ExceptionCode.IMAGE_UPLOAD_CHANGED;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.config.ImageStorageProperties;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

  private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

  private final ImageStorageProperties properties;
  private final S3Client imageS3Client;
  private final S3Presigner imageS3Presigner;

  /*
  업로드 URL 요청
   */
  @Override
  public PresignedUpload createUploadUrl(String objectKey, String contentType) {
    // Content-Type도 서명 조건에 포함되므로 클라이언트는 동일한 헤더로 업로드해야 한다.
    PutObjectRequest putObjectRequest =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(contentType)
            .build();

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(properties.uploadUrlTtl())
            .putObjectRequest(putObjectRequest)
            .build();

    try {
      PresignedPutObjectRequest request = imageS3Presigner.presignPutObject(presignRequest);
      return new PresignedUpload(
          request.url().toString(),
          Map.of("Content-Type", contentType),
          Instant.now().plus(properties.uploadUrlTtl()));
    } catch (SdkException exception) {
      throw new PickUpException(IMAGE_STORAGE_UNAVAILABLE);
    }
  }

  /*
  이미지 저장 시 메타데이터 확인
   */
  @Override
  public StoredObject getObject(String objectKey) {
    try {
      HeadObjectResponse response =
          imageS3Client.headObject(
              HeadObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build());
      return new StoredObject(response.contentLength(), response.contentType(), response.eTag());
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) {
        throw new PickUpException(IMAGE_OBJECT_NOT_FOUND);
      }
      throw translate(exception, "HEAD");
    } catch (SdkException exception) {
      throw new PickUpException(IMAGE_STORAGE_UNAVAILABLE);
    }
  }

  /*
  이미지 저장 시 실제 이미지 시그니처 확인
   */
  @Override
  public byte[] readHeader(String objectKey, int lastByteIndex) {
    try {
      GetObjectRequest request =
          GetObjectRequest.builder()
              .bucket(properties.bucket())
              .key(objectKey)
              .range("bytes=0-" + lastByteIndex)
              .build();
      ResponseBytes<GetObjectResponse> response = imageS3Client.getObjectAsBytes(request);
      return response.asByteArray();
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) {
        throw new PickUpException(IMAGE_OBJECT_NOT_FOUND);
      }
      throw translate(exception, "RANGE_GET");
    } catch (SdkException exception) {
      throw new PickUpException(IMAGE_STORAGE_UNAVAILABLE);
    }
  }

  /*
  임시 이미지 -> 실제 경로 복사
   */
  @Override
  public void copyToFinalObject(
      String sourceObjectKey, String targetObjectKey, String sourceETag, String contentType) {
    CopyObjectRequest request =
        CopyObjectRequest.builder()
            .copySource(properties.bucket() + "/" + sourceObjectKey)
            // 검증 후 임시 객체가 덮어써졌다면 검사하지 않은 파일의 확정을 막는다.
            .copySourceIfMatch(sourceETag)
            .destinationBucket(properties.bucket())
            .destinationKey(targetObjectKey)
            .metadataDirective(MetadataDirective.REPLACE)
            .contentType(contentType)
            .cacheControl(IMMUTABLE_CACHE_CONTROL)
            .build();

    try {
      imageS3Client.copyObject(request);
    } catch (S3Exception exception) {
      if (exception.statusCode() == 412) {
        throw new PickUpException(IMAGE_UPLOAD_CHANGED);
      }
      throw translate(exception, "COPY");
    } catch (SdkException exception) {
      throw new PickUpException(IMAGE_STORAGE_UNAVAILABLE);
    }
  }

  /*
  이미지 삭제 요청
   */
  @Override
  public void deleteObject(String objectKey) {
    try {
      imageS3Client.deleteObject(
          DeleteObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build());
    } catch (S3Exception exception) {
      throw translate(exception, "DELETE");
    } catch (SdkException exception) {
      throw new PickUpException(IMAGE_STORAGE_UNAVAILABLE);
    }
  }

  private PickUpException translate(S3Exception exception, String operation) {
    log.error(
        "S3 image operation failed. operation={}, status={}, errorCode={}, requestId={}",
        operation,
        exception.statusCode(),
        exception.awsErrorDetails().errorCode(),
        exception.requestId());
    return new PickUpException(IMAGE_STORAGE_UNAVAILABLE);
  }
}
