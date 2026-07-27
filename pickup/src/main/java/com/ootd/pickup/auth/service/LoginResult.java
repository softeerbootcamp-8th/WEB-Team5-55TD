package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.dto.LoginResponse;
import com.ootd.pickup.auth.token.GeneratedAccessToken;

public record LoginResult(
        LoginResponse response,
        GeneratedAccessToken accessToken,
        String refreshToken
) {
}
