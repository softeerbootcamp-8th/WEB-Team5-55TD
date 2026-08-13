package com.ootd.pickup.consignments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.ootd.pickup.consignments.dto.request.ConsignmentImageRequest;
import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.service.ImageService;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsignmentApplicationServiceTest {

  private static final String TEMPORARY_OBJECT_KEY =
      "uploads/1/consignments/00000000-0000-0000-0000-000000000001.jpg";
  private static final String FINAL_OBJECT_KEY =
      "media/consignments/1/10000000-0000-0000-0000-000000000001.jpg";

  @Mock private ConsignmentService consignmentService;

  @Mock private ImageService imageService;

  private ConsignmentApplicationService applicationService;

  @BeforeEach
  void setUp() {
    applicationService = new ConsignmentApplicationService(consignmentService, imageService);
  }

  @Test
  void 상품_등록은_S3_최종화_DB_저장_임시_객체_삭제_순서로_처리한다() {
    // given
    RegisterConsignmentRequest request = registerRequest();
    FinalizedImage finalizedImage = new FinalizedImage(TEMPORARY_OBJECT_KEY, FINAL_OBJECT_KEY);
    RegisterConsignmentResponse response = mock(RegisterConsignmentResponse.class);
    given(imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(TEMPORARY_OBJECT_KEY)))
        .willReturn(List.of(finalizedImage));
    given(consignmentService.registerConsignment(1L, request, List.of(finalizedImage)))
        .willReturn(response);

    // when
    RegisterConsignmentResponse result = applicationService.registerConsignment(1L, request);

    // then
    assertThat(result).isSameAs(response);
    InOrder order = inOrder(imageService, consignmentService);
    order
        .verify(imageService)
        .finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(TEMPORARY_OBJECT_KEY));
    order.verify(consignmentService).registerConsignment(1L, request, List.of(finalizedImage));
    order.verify(imageService).deleteTemporaryImages(List.of(finalizedImage));
    then(imageService).should(never()).deleteFinalImages(List.of(finalizedImage));
  }

  @Test
  void S3_최종화_후_DB_저장에_실패하면_신규_최종_객체를_보상_삭제한다() {
    // given
    RegisterConsignmentRequest request = registerRequest();
    FinalizedImage finalizedImage = new FinalizedImage(TEMPORARY_OBJECT_KEY, FINAL_OBJECT_KEY);
    RuntimeException databaseException = new RuntimeException("database unavailable");
    given(imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(TEMPORARY_OBJECT_KEY)))
        .willReturn(List.of(finalizedImage));
    given(consignmentService.registerConsignment(1L, request, List.of(finalizedImage)))
        .willThrow(databaseException);

    // when & then
    assertThatThrownBy(() -> applicationService.registerConsignment(1L, request))
        .isSameAs(databaseException);

    then(imageService).should().deleteFinalImages(List.of(finalizedImage));
    then(imageService).should(never()).deleteTemporaryImages(List.of(finalizedImage));
  }

  @Test
  void 상품_수정_DB_저장에_성공하면_임시_객체와_제거된_기존_객체를_삭제한다() {
    // given
    ModifyConsignmentRequest request = modifyRequest();
    FinalizedImage finalizedImage = new FinalizedImage(TEMPORARY_OBJECT_KEY, FINAL_OBJECT_KEY);
    GetConsignmentDetailResponse response = mock(GetConsignmentDetailResponse.class);
    String removedObjectKey = "media/consignments/1/removed.jpg";
    given(imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(TEMPORARY_OBJECT_KEY)))
        .willReturn(List.of(finalizedImage));
    given(consignmentService.modifyConsignment(10L, 1L, request, List.of(finalizedImage)))
        .willReturn(
            new ConsignmentService.ConsignmentModificationResult(
                response, List.of(removedObjectKey)));

    // when
    GetConsignmentDetailResponse result = applicationService.modifyConsignment(10L, 1L, request);

    // then
    assertThat(result).isSameAs(response);
    then(imageService).should().deleteTemporaryImages(List.of(finalizedImage));
    then(imageService).should().deleteObjects(List.of(removedObjectKey));
  }

  @Test
  void 상품_수정_DB_저장에_실패하면_신규_최종_객체를_보상_삭제한다() {
    // given
    ModifyConsignmentRequest request = modifyRequest();
    FinalizedImage finalizedImage = new FinalizedImage(TEMPORARY_OBJECT_KEY, FINAL_OBJECT_KEY);
    RuntimeException databaseException = new RuntimeException("database unavailable");
    given(imageService.finalizeImages(1L, ImagePurpose.CONSIGNMENT, List.of(TEMPORARY_OBJECT_KEY)))
        .willReturn(List.of(finalizedImage));
    given(consignmentService.modifyConsignment(10L, 1L, request, List.of(finalizedImage)))
        .willThrow(databaseException);

    // when & then
    assertThatThrownBy(() -> applicationService.modifyConsignment(10L, 1L, request))
        .isSameAs(databaseException);
    then(imageService).should().deleteFinalImages(List.of(finalizedImage));
    then(imageService).should(never()).deleteTemporaryImages(List.of(finalizedImage));
  }

  @Test
  void 상품_삭제는_DB_삭제가_끝난_뒤_이미지_객체를_삭제한다() {
    // given
    List<String> deletedObjectKeys = List.of("media/consignments/1/deleted.jpg");
    given(consignmentService.deleteConsignment(10L, 1L)).willReturn(deletedObjectKeys);

    // when
    applicationService.deleteConsignment(10L, 1L);

    // then
    InOrder order = inOrder(consignmentService, imageService);
    order.verify(consignmentService).deleteConsignment(10L, 1L);
    order.verify(imageService).deleteObjects(deletedObjectKeys);
  }

  private RegisterConsignmentRequest registerRequest() {
    return new RegisterConsignmentRequest(
        1L, null, null, null, List.of(new ConsignmentImageRequest(TEMPORARY_OBJECT_KEY)));
  }

  private ModifyConsignmentRequest modifyRequest() {
    return new ModifyConsignmentRequest(
        null, null, null, List.of(new ConsignmentImageRequest(TEMPORARY_OBJECT_KEY)));
  }
}
