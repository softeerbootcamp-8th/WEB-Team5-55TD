package com.ootd.pickup.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
    @Size(min = 4, message = "닉네임은 4자 이상이어야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "닉네임은 공백일 수 없습니다.")
        String nickname,
    @Size(min = 4, message = "현재 비밀번호는 4자 이상이어야 합니다.") String currentPassword,
    @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "비밀번호는 공백일 수 없습니다.")
        String password,
    @Size(max = 255, message = "프로필 이미지 URL은 255자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "프로필 이미지 URL은 공백일 수 없습니다.")
        String profileImageUrl) {

  @AssertTrue(message = "수정할 회원 정보를 입력해야 합니다.")
  @JsonIgnore
  public boolean isAnyFieldPresent() {
    return nickname != null || password != null || profileImageUrl != null;
  }

  @AssertTrue(message = "비밀번호 변경 시 현재 비밀번호를 입력해야 합니다.")
  @JsonIgnore
  public boolean isCurrentPasswordPresentForPasswordChange() {
    return password == null || (currentPassword != null && !currentPassword.isBlank());
  }
}
