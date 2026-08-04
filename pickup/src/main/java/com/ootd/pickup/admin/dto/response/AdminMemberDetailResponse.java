package com.ootd.pickup.admin.dto.response;

import com.ootd.pickup.member.domain.Member;
import java.time.LocalDateTime;

public record AdminMemberDetailResponse(
    Long memberId,
    String loginId,
    String nickname,
    String profileImageUrl,
    long pointBalance,
    LocalDateTime joinedAt,
    LocalDateTime updatedAt) {

  public static AdminMemberDetailResponse of(Member member, long pointBalance) {
    return new AdminMemberDetailResponse(
        member.getMemberId(),
        member.getLoginId(),
        member.getNickname(),
        member.getProfileImageUrl(),
        pointBalance,
        member.getJoinedAt(),
        member.getUpdatedAt());
  }
}
