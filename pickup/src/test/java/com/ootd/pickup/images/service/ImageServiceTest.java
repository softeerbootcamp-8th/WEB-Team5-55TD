package com.ootd.pickup.images.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.ImageStorage;
import com.ootd.pickup.images.ImageStorage.PresignedUpload;
import com.ootd.pickup.images.ImageStorage.StoredObject;
import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.dto.CreateImageUploadRequest;
import com.ootd.pickup.images.dto.CreateImageUploadResponse;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import com.ootd.pickup.member.repository.MemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

  private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
  private static final String FIRST_TEMPORARY_KEY =
      "uploads/1/consignments/00000000-0000-0000-0000-000000000001.jpg";
  private static final String SECOND_TEMPORARY_KEY =
      "uploads/1/consignments/00000000-0000-0000-0000-000000000002.jpg";
  private static final String PNG_TEMPORARY_KEY =
      "uploads/1/consignments/00000000-0000-0000-0000-000000000003.png";
  private static final String WEBP_TEMPORARY_KEY =
      "uploads/1/consignments/00000000-0000-0000-0000-000000000004.webp";

  @Mock private MemberRepository memberRepository;

  @Mock private ImageStorage imageStorage;

  private ImageService imageService;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(memberRepository.existsById(1L)).thenReturn(true);
    imageService = new ImageService(memberRepository, imageStorage);
  }

  @Test
  void 회원과_용도가_포함된_임시_객체키와_PresignedURL을_발급한다() {
    Instant expiresAt = Instant.now().plusSeconds(300);
    given(
            imageStorage.createUploadUrl(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("image/jpeg")))
        .willReturn(
            new PresignedUpload(
                "https://s3.example.com/upload", Map.of("Content-Type", "image/jpeg"), expiresAt));

    CreateImageUploadResponse response =
        imageService.createUpload(
            1L, new CreateImageUploadRequest(ImagePurpose.CONSIGNMENT, "image/jpeg", 1024));

    assertThat(response.temporaryObjectKey()).matches("uploads/1/consignments/[0-9a-f-]{36}\\.jpg");
    assertThat(response.uploadUrl()).isEqualTo("https://s3.example.com/upload");
    assertThat(response.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
    assertThat(response.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void 존재하지_않는_회원이면_업로드_URL을_발급하지_않는다() {
    assertThatThrownBy(
            () ->
                imageService.createUpload(
                    2L, new CreateImageUploadRequest(ImagePurpose.CONSIGNMENT, "image/jpeg", 1024)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.MEMBER_NOT_FOUND.getMessage());

    then(imageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 선언한_이미지_크기가_정확히_10MiB면_업로드_URL을_발급한다() {
    Instant expiresAt = Instant.now().plusSeconds(300);
    given(imageStorage.createUploadUrl(org.mockito.ArgumentMatchers.anyString(), eq("image/png")))
        .willReturn(
            new PresignedUpload(
                "https://s3.example.com/upload", Map.of("Content-Type", "image/png"), expiresAt));

    CreateImageUploadResponse response =
        imageService.createUpload(
            1L,
            new CreateImageUploadRequest(ImagePurpose.CONSIGNMENT, "image/png", MAX_IMAGE_SIZE));

    assertThat(response.temporaryObjectKey()).endsWith(".png");
  }

  @Test
  void 선언한_이미지_크기가_0이면_업로드_URL을_발급하지_않는다() {
    assertThatThrownBy(
            () ->
                imageService.createUpload(
                    1L, new CreateImageUploadRequest(ImagePurpose.PROFILE, "image/jpeg", 0)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_SIZE.getMessage());

    then(imageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 선언한_이미지_크기가_10MiB를_초과하면_업로드_URL을_발급하지_않는다() {
    assertThatThrownBy(
            () ->
                imageService.createUpload(
                    1L,
                    new CreateImageUploadRequest(
                        ImagePurpose.PROFILE, "image/jpeg", MAX_IMAGE_SIZE + 1)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_SIZE.getMessage());

    then(imageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 지원하지_않는_ContentType이면_업로드_URL을_발급하지_않는다() {
    assertThatThrownBy(
            () ->
                imageService.createUpload(
                    1L, new CreateImageUploadRequest(ImagePurpose.PROFILE, "image/gif", 1024)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_CONTENT_TYPE.getMessage());

    then(imageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 업로드된_JPEG을_검증하고_최종_객체로_복사한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/jpeg", "etag"));
    given(imageStorage.readHeader(FIRST_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    List<FinalizedImage> result =
        imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY));

    assertThat(result).hasSize(1);
    String objectKey = result.getFirst().objectKey();
    assertThat(result.getFirst().temporaryObjectKey()).isEqualTo(FIRST_TEMPORARY_KEY);
    assertThat(objectKey).matches("media/consignments/1/[0-9a-f-]{36}\\.jpg");
    assertThat(objectKey).doesNotEndWith("00000000-0000-0000-0000-000000000001.jpg");
    then(imageStorage)
        .should()
        .copyToFinalObject(FIRST_TEMPORARY_KEY, objectKey, "etag", "image/jpeg");
  }

  @Test
  void 같은_임시_객체를_여러번_최종화해도_서로_다른_최종_객체키를_사용한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/jpeg", "etag"));
    given(imageStorage.readHeader(FIRST_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    FinalizedImage first =
        imageService
            .finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY))
            .getFirst();
    FinalizedImage second =
        imageService
            .finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY))
            .getFirst();

    assertThat(first.objectKey()).isNotEqualTo(second.objectKey());
  }

  @Test
  void 같은_임시_객체키가_한_요청에_중복되면_거부한다() {
    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    1L,
                    ImagePurpose.CONSIGNMENT,
                    List.of(FIRST_TEMPORARY_KEY, FIRST_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.DUPLICATE_IMAGE_UPLOAD.getMessage());

    then(imageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 잘못된_형식의_임시_객체키는_거부한다() {
    List<String> invalidObjectKeys =
        List.of(
            "media/1/consignments/00000000-0000-0000-0000-000000000001.jpg",
            "uploads/1/consignments/not-a-uuid.jpg",
            "uploads/1/consignments/extra/00000000-0000-0000-0000-000000000001.jpg");

    for (String invalidObjectKey : invalidObjectKeys) {
      assertThatThrownBy(
              () ->
                  imageService.finalizeImages(
                      1L, ImagePurpose.CONSIGNMENT, List.of(invalidObjectKey)))
          .isInstanceOf(PickUpException.class)
          .hasMessage(ExceptionCode.INVALID_IMAGE_OBJECT_KEY.getMessage());
    }

    then(imageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 다른_회원의_임시_객체키는_거부한다() {
    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    2L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.IMAGE_UPLOAD_OWNER_MISMATCH.getMessage());

    then(imageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 상품용_임시_객체키를_프로필에_사용할_수_없다() {
    assertThatThrownBy(
            () ->
                imageService.finalizeImages(1L, ImagePurpose.PROFILE, List.of(FIRST_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.IMAGE_UPLOAD_PURPOSE_MISMATCH.getMessage());
  }

  @Test
  void 이미지_MIME과_파일_시그니처가_다르면_거부한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/jpeg", "etag"));
    given(imageStorage.readHeader(FIRST_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {0x00, 0x01, 0x02});

    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_CONTENT.getMessage());

    then(imageStorage)
        .should(never())
        .copyToFinalObject(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void 객체_확장자와_저장된_ContentType이_다르면_거부한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/png", "etag"));

    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_CONTENT_TYPE.getMessage());

    then(imageStorage).should(never()).readHeader(FIRST_TEMPORARY_KEY, 11);
  }

  @Test
  void 저장된_객체_크기가_0이면_거부한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(0, "image/jpeg", "etag"));

    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_SIZE.getMessage());
  }

  @Test
  void 저장된_객체_크기가_10MiB를_초과하면_거부한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(MAX_IMAGE_SIZE + 1, "image/jpeg", "etag"));

    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_SIZE.getMessage());
  }

  @Test
  void 업로드된_PNG_시그니처가_정상이면_최종_객체로_복사한다() {
    given(imageStorage.getObject(PNG_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/png", "png-etag"));
    given(imageStorage.readHeader(PNG_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

    FinalizedImage result =
        imageService
            .finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(PNG_TEMPORARY_KEY))
            .getFirst();

    assertThat(result.objectKey()).endsWith(".png");
    then(imageStorage)
        .should()
        .copyToFinalObject(PNG_TEMPORARY_KEY, result.objectKey(), "png-etag", "image/png");
  }

  @Test
  void 업로드된_WebP_시그니처가_정상이면_최종_객체로_복사한다() {
    given(imageStorage.getObject(WEBP_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/webp", "webp-etag"));
    given(imageStorage.readHeader(WEBP_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});

    FinalizedImage result =
        imageService
            .finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(WEBP_TEMPORARY_KEY))
            .getFirst();

    assertThat(result.objectKey()).endsWith(".webp");
    then(imageStorage)
        .should()
        .copyToFinalObject(WEBP_TEMPORARY_KEY, result.objectKey(), "webp-etag", "image/webp");
  }

  @Test
  void WebP_헤더가_12바이트보다_짧으면_거부한다() {
    given(imageStorage.getObject(WEBP_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/webp", "etag"));
    given(imageStorage.readHeader(WEBP_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {'R', 'I', 'F', 'F'});

    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    1L, ImagePurpose.CONSIGNMENT, List.of(WEBP_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_IMAGE_CONTENT.getMessage());
  }

  @Test
  void 여러_이미지_처리_중_실패하면_이미_복사한_최종_객체를_삭제한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/jpeg", "first-etag"));
    given(imageStorage.readHeader(FIRST_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    given(imageStorage.getObject(SECOND_TEMPORARY_KEY))
        .willThrow(new PickUpException(ExceptionCode.IMAGE_OBJECT_NOT_FOUND));

    assertThatThrownBy(
            () ->
                imageService.finalizeImages(
                    1L,
                    ImagePurpose.CONSIGNMENT,
                    List.of(FIRST_TEMPORARY_KEY, SECOND_TEMPORARY_KEY)))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.IMAGE_OBJECT_NOT_FOUND.getMessage());

    ArgumentCaptor<String> copiedObjectKey = ArgumentCaptor.forClass(String.class);
    then(imageStorage)
        .should()
        .copyToFinalObject(
            org.mockito.ArgumentMatchers.eq(FIRST_TEMPORARY_KEY),
            copiedObjectKey.capture(),
            org.mockito.ArgumentMatchers.eq("first-etag"),
            org.mockito.ArgumentMatchers.eq("image/jpeg"));
    then(imageStorage).should().deleteObject(copiedObjectKey.getValue());
  }

  @Test
  void DB_저장에_성공하면_임시_객체를_삭제할_수_있다() {
    FinalizedImage finalizedImage =
        new FinalizedImage(FIRST_TEMPORARY_KEY, "media/consignments/1/final.jpg");

    imageService.deleteTemporaryImages(List.of(finalizedImage));

    then(imageStorage).should().deleteObject(FIRST_TEMPORARY_KEY);
    then(imageStorage).should(never()).deleteObject(finalizedImage.objectKey());
  }

  @Test
  void DB_저장에_실패하면_새_최종_객체를_보상_삭제할_수_있다() {
    FinalizedImage finalizedImage =
        new FinalizedImage(FIRST_TEMPORARY_KEY, "media/consignments/1/final.jpg");

    imageService.deleteFinalImages(List.of(finalizedImage));

    then(imageStorage).should().deleteObject(finalizedImage.objectKey());
    then(imageStorage).should(never()).deleteObject(FIRST_TEMPORARY_KEY);
  }

  @Test
  void DB_변경에_성공하면_더는_참조하지_않는_기존_객체를_삭제할_수_있다() {
    String previousObjectKey = "media/consignments/1/previous.jpg";

    imageService.deleteObjects(List.of(previousObjectKey));

    then(imageStorage).should().deleteObject(previousObjectKey);
  }

  @Test
  void DB_결과가_확정된_뒤의_S3_정리_실패는_호출자에게_전파하지_않는다() {
    String obsoleteObjectKey = "media/consignments/1/obsolete.jpg";
    willThrow(new PickUpException(ExceptionCode.IMAGE_STORAGE_UNAVAILABLE))
        .given(imageStorage)
        .deleteObject(obsoleteObjectKey);

    assertThatCode(() -> imageService.deleteObjects(List.of(obsoleteObjectKey)))
        .doesNotThrowAnyException();
  }
}
