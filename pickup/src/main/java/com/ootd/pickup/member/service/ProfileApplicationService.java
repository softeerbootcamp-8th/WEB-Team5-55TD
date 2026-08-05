package com.ootd.pickup.member.service;

import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.service.ImageService;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.ProfileImageAction;
import com.ootd.pickup.member.dto.ProfileImageUpdateRequest;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileApplicationService {

  private final MemberService memberService;
  private final ImageService imageService;

  public MyProfileResponse updateMyProfile(Long memberId, UpdateMyProfileRequest request) {
    ProfileImageUpdateRequest profileImageUpdate = request.profileImageUpdate();
    List<FinalizedImage> finalizedImages = finalizeProfileImage(memberId, profileImageUpdate);
    String finalizedObjectKey =
        finalizedImages.isEmpty() ? null : finalizedImages.getFirst().objectKey();

    MemberService.ProfileUpdateResult result;
    try {
      result = memberService.updateMyProfile(memberId, request, finalizedObjectKey);
    } catch (RuntimeException exception) {
      imageService.deleteFinalImages(finalizedImages);
      throw exception;
    }

    imageService.deleteTemporaryImages(finalizedImages);
    if (profileImageUpdate != null && result.previousObjectKey() != null) {
      imageService.deleteObjects(List.of(result.previousObjectKey()));
    }
    return result.response();
  }

  private List<FinalizedImage> finalizeProfileImage(
      Long memberId, ProfileImageUpdateRequest profileImageUpdate) {
    if (profileImageUpdate == null || profileImageUpdate.action() == ProfileImageAction.REMOVE) {
      return List.of();
    }
    return imageService.finalizeImages(
        memberId, ImagePurpose.PROFILE, List.of(profileImageUpdate.temporaryObjectKey()));
  }
}
