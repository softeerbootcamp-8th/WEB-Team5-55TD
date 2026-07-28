package com.ootd.pickup.auth.token;

import com.ootd.pickup.global.auth.Authentication;

public interface AccessTokenVerifier {
    Authentication verify(String accessToken);
}
