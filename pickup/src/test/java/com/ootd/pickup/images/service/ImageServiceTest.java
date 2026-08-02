package com.ootd.pickup.images.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
import com.ootd.pickup.member.service.MemberManageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

  private static final String FIRST_TEMPORARY_KEY =
      "uploads/1/consignments/00000000-0000-0000-0000-000000000001.jpg";
  private static final String FIRST_OBJECT_KEY =
      "media/consignments/1/00000000-0000-0000-0000-000000000001.jpg";
  private static final String SECOND_TEMPORARY_KEY =
      "uploads/1/consignments/00000000-0000-0000-0000-000000000002.jpg";

  @Mock private MemberManageService memberManageService;

  @Mock private ImageStorage imageStorage;

  private ImageService imageService;

  @BeforeEach
  void setUp() {
    imageService = new ImageService(memberManageService, imageStorage);
  }

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
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
  void 업로드된_JPEG을_검증하고_최종_객체로_복사한다() {
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/jpeg", "etag"));
    given(imageStorage.readHeader(FIRST_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    List<FinalizedImage> result =
        imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY));

    assertThat(result).containsExactly(new FinalizedImage(FIRST_TEMPORARY_KEY, FIRST_OBJECT_KEY));
    then(imageStorage)
        .should()
        .copyToFinalObject(FIRST_TEMPORARY_KEY, FIRST_OBJECT_KEY, "etag", "image/jpeg");
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

    then(imageStorage).should().deleteObject(FIRST_OBJECT_KEY);
  }

  @Test
  void 트랜잭션이_커밋되면_임시_객체를_삭제한다() {
    TransactionSynchronizationManager.initSynchronization();
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/jpeg", "etag"));
    given(imageStorage.readHeader(FIRST_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY));
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCommit();
      synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }

    then(imageStorage).should().deleteObject(FIRST_TEMPORARY_KEY);
    then(imageStorage).should(never()).deleteObject(FIRST_OBJECT_KEY);
  }

  @Test
  void 트랜잭션이_롤백되면_새_최종_객체를_삭제한다() {
    TransactionSynchronizationManager.initSynchronization();
    given(imageStorage.getObject(FIRST_TEMPORARY_KEY))
        .willReturn(new StoredObject(1024, "image/jpeg", "etag"));
    given(imageStorage.readHeader(FIRST_TEMPORARY_KEY, 11))
        .willReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(FIRST_TEMPORARY_KEY));
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    then(imageStorage).should().deleteObject(FIRST_OBJECT_KEY);
    then(imageStorage).should(never()).deleteObject(FIRST_TEMPORARY_KEY);
  }
}
