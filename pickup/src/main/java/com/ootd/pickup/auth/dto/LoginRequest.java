package com.ootd.pickup.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 로그인은 값이 있는지만 본다. 형식 검증은 가입·수정에서만 한다 — 정책을 바꾸기 전에 만들어진 계정도 그대로 로그인할 수 있어야 하기 때문이다. */
public record LoginRequest(
    @NotBlank(message = "아이디는 필수입니다.") String loginId,
    @NotBlank(message = "비밀번호는 필수입니다.") String password) {}
