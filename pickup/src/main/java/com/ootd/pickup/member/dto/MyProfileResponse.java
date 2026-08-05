package com.ootd.pickup.member.dto;

import com.ootd.pickup.member.domain.Member;

public record MyProfileResponse(
    Long memberId, String loginId, String nickname, String profileImageUrl) {

  public static MyProfileResponse from(Member member) {
    return new MyProfileResponse(
        member.getMemberId(),
        member.getLoginId(),
        member.getNickname(),
        member.getProfileImageUrl());
  }
}
