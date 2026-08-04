package com.ootd.pickup.admin.dto.response;

import com.ootd.pickup.member.domain.Member;
import java.time.LocalDateTime;

public record AdminMemberListItemResponse(
    Long memberId, String loginId, String nickname, long pointBalance, LocalDateTime joinedAt) {

  public static AdminMemberListItemResponse of(Member member, long pointBalance) {
    return new AdminMemberListItemResponse(
        member.getMemberId(),
        member.getLoginId(),
        member.getNickname(),
        pointBalance,
        member.getJoinedAt());
  }
}
