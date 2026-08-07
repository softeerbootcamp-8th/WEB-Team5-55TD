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

  public static AdminMemberDetailResponse of(
      Member member, long pointBalance, String profileImageUrl) {
    return new AdminMemberDetailResponse(
        member.getMemberId(),
        member.getLoginId(),
        member.getNickname(),
        profileImageUrl,
        pointBalance,
        member.getJoinedAt(),
        member.getUpdatedAt());
  }
}
