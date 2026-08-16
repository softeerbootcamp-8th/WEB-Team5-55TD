package com.ootd.pickup.auth.dto;

public record LoginResponseBody(
    Long memberId, String loginId, String nickname, String profileImageUrl, boolean needsNickname) {
  public LoginResponseBody(Long memberId, String loginId, String nickname, String profileImageUrl) {
    this(memberId, loginId, nickname, profileImageUrl, false);
  }
}
