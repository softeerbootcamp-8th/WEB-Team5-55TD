package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.dto.LoginResponse;
import com.ootd.pickup.auth.token.AccessToken;

public record LoginResult(
        LoginResponse response,
        AccessToken accessToken,
        String refreshToken
) {
}
