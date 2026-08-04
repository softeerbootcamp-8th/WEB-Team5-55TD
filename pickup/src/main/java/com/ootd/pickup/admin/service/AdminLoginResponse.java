package com.ootd.pickup.admin.service;

import com.ootd.pickup.admin.dto.response.AdminLoginResponseBody;
import com.ootd.pickup.auth.token.AccessToken;

public record AdminLoginResponse(AdminLoginResponseBody body, AccessToken accessToken) {}
