package com.ootd.pickup.images.service;

import static com.ootd.pickup.global.exception.ExceptionCode.DUPLICATE_IMAGE_UPLOAD;
import static com.ootd.pickup.global.exception.ExceptionCode.IMAGE_UPLOAD_OWNER_MISMATCH;
import static com.ootd.pickup.global.exception.ExceptionCode.IMAGE_UPLOAD_PURPOSE_MISMATCH;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_IMAGE_CONTENT;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_IMAGE_CONTENT_TYPE;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_IMAGE_OBJECT_KEY;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_IMAGE_SIZE;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.ImageStorage;
import com.ootd.pickup.images.ImageStorage.PresignedUpload;
import com.ootd.pickup.images.ImageStorage.StoredObject;
import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.dto.CreateImageUploadRequest;
import com.ootd.pickup.images.dto.CreateImageUploadResponse;
import com.ootd.pickup.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

  private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
  private static final int IMAGE_HEADER_LAST_BYTE_INDEX = 11;
  private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
  };
  private static final Pattern FILE_NAME_PATTERN =
      Pattern.compile(
          "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");
  private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE =
      Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
  private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION =
      Map.of("jpg", "image/jpeg", "png", "image/png", "webp", "image/webp");

  private final MemberRepository memberRepository;
  private final ImageStorage imageStorage;

  /*
  업로드 URL 발급
   */
  public CreateImageUploadResponse createUpload(Long memberId, CreateImageUploadRequest request) {
    validateDeclaredImage(request.contentType(), request.contentLength());
    if (!memberRepository.existsById(memberId)) {
      throw new PickUpException(MEMBER_NOT_FOUND);
    }

    String extension = EXTENSION_BY_CONTENT_TYPE.get(request.contentType());
    String temporaryObjectKey =
        "uploads/%d/%s/%s.%s"
            .formatted(memberId, request.purpose().getDirectory(), UUID.randomUUID(), extension);
    PresignedUpload upload =
        imageStorage.createUploadUrl(temporaryObjectKey, request.contentType());

    return new CreateImageUploadResponse(
        temporaryObjectKey, upload.uploadUrl(), upload.requiredHeaders(), upload.expiresAt());
  }

  /*
  이미지 최종 확정
   */
  public List<FinalizedImage> finalizeImages(
      Long memberId, ImagePurpose purpose, List<String> temporaryObjectKeys) {
    // 중복 키 검사
    if (temporaryObjectKeys.size() != new HashSet<>(temporaryObjectKeys).size()) {
      throw new PickUpException(DUPLICATE_IMAGE_UPLOAD);
    }

    List<FinalizedImage> finalizedImages = new ArrayList<>();
    try {
      for (String temporaryObjectKey : temporaryObjectKeys) {
        finalizedImages.add(finalizeImage(memberId, purpose, temporaryObjectKey));
      }
    } catch (RuntimeException exception) {
      // 일부 이미지만 복사된 채 남지 않도록 이번 요청에서 만든 최종 객체를 되돌린다.
      deleteIgnoringFailure(
          finalizedImages.stream().map(FinalizedImage::objectKey).toList(), "copy-compensation");
      throw exception;
    }

    return List.copyOf(finalizedImages);
  }

  public void deleteTemporaryImages(List<FinalizedImage> finalizedImages) {
    deleteIgnoringFailure(
        finalizedImages.stream().map(FinalizedImage::temporaryObjectKey).toList(),
        "temporary-after-db-commit");
  }

  public void deleteFinalImages(List<FinalizedImage> finalizedImages) {
    deleteIgnoringFailure(
        finalizedImages.stream().map(FinalizedImage::objectKey).toList(), "db-compensation");
  }

  public void deleteObjects(List<String> objectKeys) {
    deleteIgnoringFailure(List.copyOf(objectKeys), "obsolete-after-db-commit");
  }

  /*
  이미지 한 장 검사 및 복사
   */
  private FinalizedImage finalizeImage(
      Long memberId, ImagePurpose purpose, String temporaryObjectKey) {
    ParsedObjectKey parsedObjectKey = parseObjectKey(temporaryObjectKey);
    validateScope(parsedObjectKey, memberId, purpose);

    StoredObject storedObject = imageStorage.getObject(temporaryObjectKey);
    validateStoredObject(parsedObjectKey.extension(), storedObject);
    // 파일 전체를 내려받지 않고 앞 12바이트만 읽어 JPEG·PNG·WebP 시그니처를 확인한다.
    byte[] header = imageStorage.readHeader(temporaryObjectKey, IMAGE_HEADER_LAST_BYTE_INDEX);
    validateSignature(storedObject.contentType(), header);

    // 같은 임시 키를 동시에 최종화해도 각 요청의 보상 삭제가 서로 영향을 주지 않게 분리한다.
    String objectKey =
        "media/%s/%d/%s.%s"
            .formatted(
                purpose.getDirectory(), memberId, UUID.randomUUID(), parsedObjectKey.extension());
    imageStorage.copyToFinalObject(
        temporaryObjectKey, objectKey, storedObject.eTag(), storedObject.contentType());
    return new FinalizedImage(temporaryObjectKey, objectKey);
  }

  /*
  임시 객체 키 검증
   */
  private ParsedObjectKey parseObjectKey(String objectKey) {
    if (objectKey == null) {
      throw new PickUpException(INVALID_IMAGE_OBJECT_KEY);
    }
    // 정상적인 키는 정확히 네 부분이어야 함
    String[] parts = objectKey.split("/", -1);
    if (parts.length != 4
        || !"uploads".equals(parts[0])
        || !FILE_NAME_PATTERN.matcher(parts[3]).matches()) {
      throw new PickUpException(INVALID_IMAGE_OBJECT_KEY);
    }

    long ownerMemberId;
    try {
      ownerMemberId = Long.parseLong(parts[1]);
      // 파일 이름에서 확장자를 제외하고 36글자를 가져옴 -> 정상 형식이 아니면 IllegalArgumetException
      UUID.fromString(parts[3].substring(0, 36));
    } catch (IllegalArgumentException exception) {
      throw new PickUpException(INVALID_IMAGE_OBJECT_KEY);
    }
    String extension = parts[3].substring(parts[3].lastIndexOf('.') + 1);
    return new ParsedObjectKey(ownerMemberId, parts[2], extension);
  }

  /*
  소유자와 용도 검사
   */
  private void validateScope(ParsedObjectKey parsedObjectKey, Long memberId, ImagePurpose purpose) {
    if (parsedObjectKey.ownerMemberId() != memberId) {
      throw new PickUpException(IMAGE_UPLOAD_OWNER_MISMATCH);
    }
    if (!parsedObjectKey.directory().equals(purpose.getDirectory())) {
      throw new PickUpException(IMAGE_UPLOAD_PURPOSE_MISMATCH);
    }
  }

  private void validateDeclaredImage(String contentType, long contentLength) {
    if (!EXTENSION_BY_CONTENT_TYPE.containsKey(contentType)) {
      throw new PickUpException(INVALID_IMAGE_CONTENT_TYPE);
    }
    if (contentLength <= 0 || contentLength > MAX_IMAGE_SIZE) {
      throw new PickUpException(INVALID_IMAGE_SIZE);
    }
  }

  // 실제 크기와 Content-Type 검사
  private void validateStoredObject(String extension, StoredObject storedObject) {
    if (storedObject.contentLength() <= 0 || storedObject.contentLength() > MAX_IMAGE_SIZE) {
      throw new PickUpException(INVALID_IMAGE_SIZE);
    }
    if (!CONTENT_TYPE_BY_EXTENSION.get(extension).equals(storedObject.contentType())) {
      throw new PickUpException(INVALID_IMAGE_CONTENT_TYPE);
    }
  }

  // 파일 내용 검사
  private void validateSignature(String contentType, byte[] header) {
    boolean valid =
        switch (contentType) {
          case "image/jpeg" -> startsWith(header, JPEG_SIGNATURE);
          case "image/png" -> startsWith(header, PNG_SIGNATURE);
          case "image/webp" ->
              header.length >= 12
                  && asciiEquals(header, 0, "RIFF")
                  && asciiEquals(header, 8, "WEBP");
          default -> false;
        };
    if (!valid) {
      throw new PickUpException(INVALID_IMAGE_CONTENT);
    }
  }

  private boolean startsWith(byte[] actual, byte[] expected) {
    return actual.length >= expected.length
        && Arrays.equals(Arrays.copyOf(actual, expected.length), expected);
  }

  private boolean asciiEquals(byte[] actual, int offset, String expected) {
    if (actual.length < offset + expected.length()) {
      return false;
    }
    for (int index = 0; index < expected.length(); index++) {
      if (actual[offset + index] != (byte) expected.charAt(index)) {
        return false;
      }
    }
    return true;
  }

  // 이미 결정된 DB 결과를 뒤집을 수 없으므로 정리 실패는 기록하고 원래 요청 결과는 유지한다.
  private void deleteIgnoringFailure(List<String> objectKeys, String operation) {
    for (String objectKey : objectKeys) {
      try {
        imageStorage.deleteObject(objectKey);
      } catch (RuntimeException exception) {
        log.error(
            "Image cleanup failed. operation={}, objectKey={}", operation, objectKey, exception);
      }
    }
  }

  public record FinalizedImage(String temporaryObjectKey, String objectKey) {}

  private record ParsedObjectKey(long ownerMemberId, String directory, String extension) {}
}
