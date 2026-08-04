package com.ootd.pickup.auth.token;

import com.ootd.pickup.global.auth.AdminAuthentication;

public interface AdminAccessTokenVerifier {
  AdminAuthentication verify(String accessToken);
}
