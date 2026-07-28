package com.ootd.pickup.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberRequest(
    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, message = "아이디는 4자 이상이어야 합니다.")
    String loginId,
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 4, message = "닉네임은 4자 이상이어야 합니다.")
    String nickname,
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.")
    String password
) {
}
