package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenHash = refreshTokenGenerator.hash(refreshToken);
        refreshTokenRepository.delete(tokenHash);
    }
}
