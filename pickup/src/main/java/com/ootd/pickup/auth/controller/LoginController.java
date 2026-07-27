package com.ootd.pickup.auth.controller;

import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponse;
import com.ootd.pickup.auth.service.LoginResult;
import com.ootd.pickup.auth.service.LoginService;
import com.ootd.pickup.auth.web.TokenCookieManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginController {
    private final LoginService loginService;
    private final TokenCookieManager tokenCookieManager;

    @PostMapping
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResult loginResult = loginService.login(loginRequest);
        return ResponseEntity.ok()
                .headers(tokenCookieManager.createTokenCookieHeaders(
                        loginResult.accessToken(),
                        loginResult.refreshToken()
                ))
                .body(loginResult.response());
    }
}
