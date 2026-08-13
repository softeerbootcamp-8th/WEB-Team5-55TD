package com.ootd.pickup.member.dto;

import com.ootd.pickup.member.domain.Member;

/** oauthProvider 는 소셜 가입 회원만 값을 가진다(예: "KAKAO"). 일반 회원은 null 이다. */
public record MyProfileResponse(
    Long memberId,
    String loginId,
    String nickname,
    String profileImageUrl,
    String oauthProvider) {

  public static MyProfileResponse from(Member member, String profileImageUrl) {
    return new MyProfileResponse(
        member.getMemberId(),
        member.getLoginId(),
        member.getNickname(),
        profileImageUrl,
        member.getOauthProvider());
  }
}
