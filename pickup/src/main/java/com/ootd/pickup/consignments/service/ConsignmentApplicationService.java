package com.ootd.pickup.consignments.service;

import static com.ootd.pickup.global.exception.ExceptionCode.DUPLICATE_IMAGE_UPLOAD;
import static com.ootd.pickup.global.exception.ExceptionCode.ILLEGAL_ARGUMENT;

import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.service.ImageService;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsignmentApplicationService {

  private final ConsignmentService consignmentService;
  private final ImageService imageService;

  public RegisterConsignmentResponse registerConsignment(
      Long sellerMemberId, RegisterConsignmentRequest request) {
    List<String> temporaryObjectKeys = validateAndGetRegistrationKeys(request);
    List<FinalizedImage> finalizedImages =
        imageService.finalizeImages(sellerMemberId, ImagePurpose.CONSIGNMENT, temporaryObjectKeys);

    RegisterConsignmentResponse response;
    try {
      response = consignmentService.registerConsignment(sellerMemberId, request, finalizedImages);
    } catch (RuntimeException exception) {
      imageService.deleteFinalImages(finalizedImages);
      throw exception;
    }

    imageService.deleteTemporaryImages(finalizedImages);
    return response;
  }

  public GetConsignmentDetailResponse modifyConsignment(
      Long consignmentId, Long sellerMemberId, ModifyConsignmentRequest request) {
    List<String> temporaryObjectKeys = validateAndGetModificationKeys(request);
    List<FinalizedImage> finalizedImages =
        imageService.finalizeImages(sellerMemberId, ImagePurpose.CONSIGNMENT, temporaryObjectKeys);

    ConsignmentService.ConsignmentModificationResult result;
    try {
      result =
          consignmentService.modifyConsignment(
              consignmentId, sellerMemberId, request, finalizedImages);
    } catch (RuntimeException exception) {
      imageService.deleteFinalImages(finalizedImages);
      throw exception;
    }

    imageService.deleteTemporaryImages(finalizedImages);
    imageService.deleteObjects(result.removedObjectKeys());
    return result.response();
  }

  public void deleteConsignment(Long consignmentId, Long sellerMemberId) {
    List<String> objectKeys = consignmentService.deleteConsignment(consignmentId, sellerMemberId);
    imageService.deleteObjects(objectKeys);
  }

  private List<String> validateAndGetRegistrationKeys(RegisterConsignmentRequest request) {
    for (var image : request.images()) {
      if (!image.isValidReference() || image.consignmentImageId() != null) {
        throw new PickUpException(ILLEGAL_ARGUMENT);
      }
    }
    return request.images().stream().map(image -> image.temporaryObjectKey()).toList();
  }

  private List<String> validateAndGetModificationKeys(ModifyConsignmentRequest request) {
    Set<Long> retainedImageIds = new HashSet<>();
    for (var image : request.images()) {
      if (!image.isValidReference()) {
        throw new PickUpException(ILLEGAL_ARGUMENT);
      }
      if (image.consignmentImageId() != null && !retainedImageIds.add(image.consignmentImageId())) {
        throw new PickUpException(DUPLICATE_IMAGE_UPLOAD);
      }
    }
    return request.images().stream()
        .filter(image -> image.temporaryObjectKey() != null)
        .map(image -> image.temporaryObjectKey())
        .toList();
  }
}
