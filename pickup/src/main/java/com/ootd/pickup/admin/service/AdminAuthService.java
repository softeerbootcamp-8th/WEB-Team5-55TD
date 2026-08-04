package com.ootd.pickup.admin.service;

import static com.ootd.pickup.global.exception.ExceptionCode.ADMIN_LOGIN_FAILED;

import com.ootd.pickup.admin.domain.Admin;
import com.ootd.pickup.admin.dto.request.AdminLoginRequest;
import com.ootd.pickup.admin.dto.response.AdminLoginResponseBody;
import com.ootd.pickup.admin.repository.AdminRepository;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.AdminAccessTokenGenerator;
import com.ootd.pickup.global.exception.PickUpException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

  private final AdminRepository adminRepository;
  private final AdminAccessTokenGenerator adminAccessTokenGenerator;

  public AdminLoginResponse login(AdminLoginRequest loginRequest) {
    Admin admin =
        adminRepository
            .findByLoginId(loginRequest.loginId())
            .orElseThrow(() -> new PickUpException(ADMIN_LOGIN_FAILED));

    if (!admin.isPasswordMatched(loginRequest.password())) {
      throw new PickUpException(ADMIN_LOGIN_FAILED);
    }

    AccessToken accessToken = adminAccessTokenGenerator.generate(admin.getAdminId());

    AdminLoginResponseBody body =
        new AdminLoginResponseBody(admin.getAdminId(), admin.getLoginId(), admin.getName());

    return new AdminLoginResponse(body, accessToken);
  }
}
