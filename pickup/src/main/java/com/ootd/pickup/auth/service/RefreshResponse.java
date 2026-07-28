package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.dto.RefreshResponseBody;
import com.ootd.pickup.auth.token.AccessToken;

public record RefreshResponse(
    RefreshResponseBody body,
    AccessToken accessToken,
    String refreshToken
) {
}
