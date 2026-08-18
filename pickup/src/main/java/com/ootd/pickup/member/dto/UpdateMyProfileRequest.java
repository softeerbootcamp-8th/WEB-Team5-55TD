package com.ootd.pickup.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
    @Size(min = 2, max = 8, message = "닉네임은 2~8자여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "닉네임은 공백일 수 없습니다.")
        String nickname,
    String currentPassword,
    @Pattern(
            regexp =
                "^(?:(?=.*[A-Za-z])(?=.*\\d)|(?=.*[A-Za-z])(?=.*[^A-Za-z\\d\\s])|(?=.*\\d)(?=.*[^A-Za-z\\d\\s]))\\S{8,16}$",
            message = "비밀번호는 영문, 숫자, 특수문자 중 두 종류 이상을 조합한 8~16자여야 합니다.")
        String password,
    @Valid ProfileImageUpdateRequest profileImageUpdate) {

  public UpdateMyProfileRequest {
    if (nickname != null) {
      nickname = nickname.strip();
    }
  }

  @AssertTrue(message = "수정할 회원 정보를 입력해야 합니다.")
  @JsonIgnore
  public boolean isAnyFieldPresent() {
    return nickname != null || password != null || profileImageUpdate != null;
  }

  @AssertTrue(message = "비밀번호 변경 시 현재 비밀번호를 입력해야 합니다.")
  @JsonIgnore
  public boolean isCurrentPasswordPresentForPasswordChange() {
    return password == null || (currentPassword != null && !currentPassword.isBlank());
  }
}
