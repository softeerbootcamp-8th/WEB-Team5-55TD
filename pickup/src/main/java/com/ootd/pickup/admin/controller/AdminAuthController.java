package com.ootd.pickup.admin.controller;

import com.ootd.pickup.admin.dto.request.AdminLoginRequest;
import com.ootd.pickup.admin.dto.response.AdminLoginResponseBody;
import com.ootd.pickup.admin.service.AdminAuthService;
import com.ootd.pickup.admin.service.AdminLoginResponse;
import com.ootd.pickup.global.auth.AdminTokenCookieManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final AdminAuthService adminAuthService;
  private final AdminTokenCookieManager adminTokenCookieManager;

  @PostMapping("/login")
  public ResponseEntity<AdminLoginResponseBody> login(
      @Valid @RequestBody AdminLoginRequest loginRequest) {
    AdminLoginResponse response = adminAuthService.login(loginRequest);
    return ResponseEntity.ok()
        .headers(adminTokenCookieManager.createTokenCookieHeaders(response.accessToken()))
        .body(response.body());
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .headers(adminTokenCookieManager.createExpiredTokenCookieHeaders())
        .build();
  }
}
