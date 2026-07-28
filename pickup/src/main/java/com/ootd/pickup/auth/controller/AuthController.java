package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.api.AuthApi;
import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponse;
import com.ootd.pickup.auth.dto.RefreshResponse;
import com.ootd.pickup.auth.service.AuthService;
import com.ootd.pickup.auth.service.LoginResult;
import com.ootd.pickup.auth.service.RefreshResult;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.auth.TokenCookieManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final AuthService authService;
    private final TokenCookieManager tokenCookieManager;

    @PostMapping
    @Override
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResult loginResult = authService.login(loginRequest);
        return ResponseEntity.ok()
                .headers(tokenCookieManager.createTokenCookieHeaders(
                        loginResult.accessToken(),
                        loginResult.refreshToken()
                ))
                .body(loginResult.response());
    }

    @PostMapping("/refresh")
    @Override
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(
                    name = AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        RefreshResult result = authService.refresh(refreshToken);

        return ResponseEntity.ok()
                .headers(tokenCookieManager.createTokenCookieHeaders(
                        result.accessToken(),
                        result.refreshToken()
                ))
                .body(new RefreshResponse(result.accessToken().expiresAt()));
    }

    @PostMapping("/logout")
    @Override
    public ResponseEntity<Void> logout(
            @CookieValue(
                    name = AuthenticationAttributes.REFRESH_TOKEN_COOKIE_NAME,
                    required = false
            ) String refreshToken
    ) {
        authService.logout(refreshToken);

        return ResponseEntity.noContent()
                .headers(tokenCookieManager.createExpiredTokenCookieHeaders())
                .build();
    }
}
