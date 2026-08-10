package com.ootd.pickup.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProfileImageUpdateRequestTest {

  @Test
  void action이_null이면_검증을_위임한다() {
    assertThat(new ProfileImageUpdateRequest(null, null).isValidUpdate()).isTrue();
  }

  @Test
  void SET은_비어있지_않은_object_key가_필요하다() {
    assertThat(
            new ProfileImageUpdateRequest(ProfileImageAction.SET, "images/profile.png")
                .isValidUpdate())
        .isTrue();
    assertThat(new ProfileImageUpdateRequest(ProfileImageAction.SET, null).isValidUpdate())
        .isFalse();
    assertThat(new ProfileImageUpdateRequest(ProfileImageAction.SET, " \t ").isValidUpdate())
        .isFalse();
  }

  @Test
  void REMOVE는_object_key를_받지_않는다() {
    assertThat(new ProfileImageUpdateRequest(ProfileImageAction.REMOVE, null).isValidUpdate())
        .isTrue();
    assertThat(new ProfileImageUpdateRequest(ProfileImageAction.REMOVE, "old.png").isValidUpdate())
        .isFalse();
  }
}
