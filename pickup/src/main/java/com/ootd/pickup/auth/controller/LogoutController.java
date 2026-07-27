package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.service.LogoutService;
import com.ootd.pickup.auth.web.AuthenticationAttributes;
import com.ootd.pickup.auth.web.TokenCookieManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LogoutController {
    private final LogoutService logoutService;
    private final TokenCookieManager tokenCookieManager;

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(
                    name = AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        logoutService.logout(refreshToken);

        return ResponseEntity.noContent()
                .headers(tokenCookieManager.createExpiredTokenCookieHeaders())
                .build();
    }
}
