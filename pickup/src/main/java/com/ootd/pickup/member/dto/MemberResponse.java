package com.ootd.pickup.member.dto;

public record MemberResponse(
        Long memberId,
        String loginId,
        String nickname,
        String profileImageUrl
) {
}
