package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.token.AccessToken;

public record RefreshResult(
        AccessToken accessToken,
        String refreshToken
) {
}
