package com.ootd.pickup.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 가입 입력 규칙은 네이버 가입 정책을 기준으로 잡았다. 로그인에는 적용하지 않는다(LoginRequest 참고). */
public record MemberRequest(
    @NotBlank(message = "아이디는 필수입니다.")
        // 영문 소문자로 시작하는 5~15자
        @Pattern(
            regexp = "^[a-z][a-z0-9_-]{4,14}$",
            message = "아이디는 영문 소문자로 시작하는 5~15자의 영문 소문자, 숫자, 특수기호(_, -)여야 합니다.")
        String loginId,
    @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 8, message = "닉네임은 2~8자여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "닉네임은 공백일 수 없습니다.")
        String nickname,
    @NotBlank(message = "비밀번호는 필수입니다.")
        // 공백 없는 8~16자에 영문·숫자·특수문자 중 두 종류 이상
        @Pattern(
            regexp =
                "^(?:(?=.*[A-Za-z])(?=.*\\d)|(?=.*[A-Za-z])(?=.*[^A-Za-z\\d\\s])|(?=.*\\d)(?=.*[^A-Za-z\\d\\s]))\\S{8,16}$",
            message = "비밀번호는 영문, 숫자, 특수문자 중 두 종류 이상을 조합한 8~16자여야 합니다.")
        String password) {

  public MemberRequest {
    if (nickname != null) {
      nickname = nickname.strip();
    }

  }
}
