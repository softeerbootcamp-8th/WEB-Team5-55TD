package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.dto.LoginResponseBody;
import com.ootd.pickup.auth.token.AccessToken;

public record LoginResponse(
    LoginResponseBody body,
    AccessToken accessToken,
    String refreshToken
) {
}
