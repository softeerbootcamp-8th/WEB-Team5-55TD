package com.ootd.pickup.auth.token;

public interface AccessTokenVerifier {
    Authentication verify(String accessToken);
}
