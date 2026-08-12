package com.ootd.pickup.member.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawMemberRequest(@NotBlank(message = "비밀번호는 필수입니다.") String password) {}
