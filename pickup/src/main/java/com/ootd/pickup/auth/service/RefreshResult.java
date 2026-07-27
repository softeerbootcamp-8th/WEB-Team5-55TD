package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.token.GeneratedAccessToken;

public record RefreshResult(
        GeneratedAccessToken accessToken,
        String refreshToken
) {
}
