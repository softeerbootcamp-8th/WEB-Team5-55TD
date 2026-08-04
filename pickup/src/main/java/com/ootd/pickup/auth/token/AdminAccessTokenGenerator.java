package com.ootd.pickup.auth.token;

public interface AdminAccessTokenGenerator {
  AccessToken generate(Long adminId);
}
