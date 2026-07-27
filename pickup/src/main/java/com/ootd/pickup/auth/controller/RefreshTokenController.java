package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.dto.RefreshResponse;
import com.ootd.pickup.auth.service.RefreshResult;
import com.ootd.pickup.auth.service.RefreshTokenService;
import com.ootd.pickup.auth.web.AuthenticationAttributes;
import com.ootd.pickup.auth.web.TokenCookieManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefreshTokenController {
    private final RefreshTokenService refreshTokenService;
    private final TokenCookieManager tokenCookieManager;

    @PostMapping("/auth/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(
                    name = AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        RefreshResult result = refreshTokenService.refresh(refreshToken);

        return ResponseEntity.ok()
                .headers(tokenCookieManager.createTokenCookieHeaders(
                        result.accessToken(),
                        result.refreshToken()
                ))
                .body(new RefreshResponse(result.accessToken().expiresAt()));
    }
}
