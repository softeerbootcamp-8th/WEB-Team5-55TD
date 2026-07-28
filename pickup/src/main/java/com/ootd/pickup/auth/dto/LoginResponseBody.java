package com.ootd.pickup.auth.dto;

public record LoginResponseBody(
    Long memberId,
    String loginId,
    String nickname,
    String profileImageUrl
) {
}
