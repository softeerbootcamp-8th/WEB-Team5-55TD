package com.ootd.pickup.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, message = "아이디는 4자 이상이어야 합니다.")
    String loginId,
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.")
    String password
) {
}
