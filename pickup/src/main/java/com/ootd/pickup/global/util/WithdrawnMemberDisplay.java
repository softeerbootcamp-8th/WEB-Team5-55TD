package com.ootd.pickup.global.util;

import com.ootd.pickup.member.domain.Member;

public final class WithdrawnMemberDisplay {

  public static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";

  private WithdrawnMemberDisplay() {}

  public static String resolveNickname(Member member, String nicknameSnapshot) {
    return member.isWithdrawn() ? WITHDRAWN_NICKNAME : nicknameSnapshot;
  }
}
