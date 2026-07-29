package com.ootd.pickup.auth.token;

public interface AccessTokenGenerator {
  AccessToken generate(Long memberId);
}
