package com.ootd.pickup.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.service.ImageService;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.ProfileImageAction;
import com.ootd.pickup.member.dto.ProfileImageUpdateRequest;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileApplicationServiceTest {

  private static final String TEMPORARY_OBJECT_KEY =
      "uploads/1/profiles/00000000-0000-0000-0000-000000000001.jpg";
  private static final String FINAL_OBJECT_KEY =
      "media/profiles/1/10000000-0000-0000-0000-000000000001.jpg";
  private static final String PREVIOUS_OBJECT_KEY = "media/profiles/1/previous.jpg";

  @Mock private MemberService memberService;

  @Mock private ImageService imageService;

  private ProfileApplicationService applicationService;

  @BeforeEach
  void setUp() {
    applicationService = new ProfileApplicationService(memberService, imageService);
  }

  @Test
  void 프로필_이미지_교체는_S3_최종화_DB_저장_불필요한_객체_삭제_순서로_처리한다() {
    UpdateMyProfileRequest request = setProfileImageRequest();
    FinalizedImage finalizedImage = new FinalizedImage(TEMPORARY_OBJECT_KEY, FINAL_OBJECT_KEY);
    MyProfileResponse response = mock(MyProfileResponse.class);
    given(imageService.finalizeImages(1L, ImagePurpose.PROFILE, List.of(TEMPORARY_OBJECT_KEY)))
        .willReturn(List.of(finalizedImage));
    given(memberService.updateMyProfile(1L, request, FINAL_OBJECT_KEY))
        .willReturn(new MemberService.ProfileUpdateResult(response, PREVIOUS_OBJECT_KEY));

    MyProfileResponse result = applicationService.updateMyProfile(1L, request);

    assertThat(result).isSameAs(response);
    InOrder order = inOrder(imageService, memberService);
    order
        .verify(imageService)
        .finalizeImages(1L, ImagePurpose.PROFILE, List.of(TEMPORARY_OBJECT_KEY));
    order.verify(memberService).updateMyProfile(1L, request, FINAL_OBJECT_KEY);
    order.verify(imageService).deleteTemporaryImages(List.of(finalizedImage));
    order.verify(imageService).deleteObjects(List.of(PREVIOUS_OBJECT_KEY));
  }

  @Test
  void S3_최종화_후_프로필_DB_저장에_실패하면_신규_최종_객체를_보상_삭제한다() {
    UpdateMyProfileRequest request = setProfileImageRequest();
    FinalizedImage finalizedImage = new FinalizedImage(TEMPORARY_OBJECT_KEY, FINAL_OBJECT_KEY);
    RuntimeException databaseException = new RuntimeException("database unavailable");
    given(imageService.finalizeImages(1L, ImagePurpose.PROFILE, List.of(TEMPORARY_OBJECT_KEY)))
        .willReturn(List.of(finalizedImage));
    given(memberService.updateMyProfile(1L, request, FINAL_OBJECT_KEY))
        .willThrow(databaseException);

    assertThatThrownBy(() -> applicationService.updateMyProfile(1L, request))
        .isSameAs(databaseException);

    then(imageService).should().deleteFinalImages(List.of(finalizedImage));
    then(imageService).should(never()).deleteTemporaryImages(List.of(finalizedImage));
    then(imageService).should(never()).deleteObjects(List.of(PREVIOUS_OBJECT_KEY));
  }

  @Test
  void 프로필_이미지_삭제는_DB에서_참조를_제거한_뒤_기존_객체를_삭제한다() {
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(
            null, null, null, new ProfileImageUpdateRequest(ProfileImageAction.REMOVE, null));
    MyProfileResponse response = mock(MyProfileResponse.class);
    given(memberService.updateMyProfile(1L, request, null))
        .willReturn(new MemberService.ProfileUpdateResult(response, PREVIOUS_OBJECT_KEY));

    MyProfileResponse result = applicationService.updateMyProfile(1L, request);

    assertThat(result).isSameAs(response);
    InOrder order = inOrder(memberService, imageService);
    order.verify(memberService).updateMyProfile(1L, request, null);
    order.verify(imageService).deleteObjects(List.of(PREVIOUS_OBJECT_KEY));
    then(imageService).should(never()).finalizeImages(1L, ImagePurpose.PROFILE, List.of());
  }

  private UpdateMyProfileRequest setProfileImageRequest() {
    return new UpdateMyProfileRequest(
        null,
        null,
        null,
        new ProfileImageUpdateRequest(ProfileImageAction.SET, TEMPORARY_OBJECT_KEY));
  }
}
