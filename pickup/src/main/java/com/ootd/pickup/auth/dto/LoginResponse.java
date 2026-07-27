package com.ootd.pickup.auth.dto;

public record LoginResponse(
        Long memberId,
        String loginId,
        String nickname,
        String profileImageUrl
) { }
