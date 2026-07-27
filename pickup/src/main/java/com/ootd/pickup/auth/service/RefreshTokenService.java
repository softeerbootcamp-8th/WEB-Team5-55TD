package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.GeneratedAccessToken;
import com.ootd.pickup.auth.token.GeneratedRefreshToken;
import com.ootd.pickup.auth.token.JwtTokenProperties;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AccessTokenGenerator accessTokenGenerator;
    private final JwtTokenProperties jwtTokenProperties;

    public RefreshResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new PickUpException(ExceptionCode.INVALID_REFRESH_TOKEN);
        }

        String oldTokenHash = refreshTokenGenerator.hash(refreshToken);
        Long memberId = refreshTokenRepository.consume(oldTokenHash)
                .orElseThrow(() -> new PickUpException(ExceptionCode.INVALID_REFRESH_TOKEN));

        GeneratedRefreshToken newRefreshToken = refreshTokenGenerator.generate();
        refreshTokenRepository.save(
                newRefreshToken.hash(),
                memberId,
                jwtTokenProperties.refreshTokenTtl()
        );

        GeneratedAccessToken newAccessToken = accessTokenGenerator.generate(memberId);
        return new RefreshResult(newAccessToken, newRefreshToken.value());
    }
}
