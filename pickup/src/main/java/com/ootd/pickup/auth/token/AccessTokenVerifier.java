package com.ootd.pickup.auth.token;

public interface AccessTokenVerifier {
    AuthenticatedToken verify(String accessToken);
}
