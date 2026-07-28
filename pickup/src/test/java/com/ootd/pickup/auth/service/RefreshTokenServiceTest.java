package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.RefreshToken;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private AccessTokenGenerator accessTokenGenerator;

    @Mock
    private JwtTokenProperties jwtTokenProperties;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void 유효한_리프레시_토큰을_새_토큰으로_교체한다() {
        Duration refreshTokenTtl = Duration.ofDays(14);
        RefreshToken newRefreshToken = new RefreshToken(
                "new-refresh-token",
                "new-refresh-token-hash"
        );
        AccessToken newAccessToken = new AccessToken(
                "new-access-token",
                Instant.now().plusSeconds(900)
        );
        given(refreshTokenGenerator.hash("old-refresh-token"))
                .willReturn("old-refresh-token-hash");
        given(refreshTokenRepository.consume("old-refresh-token-hash"))
                .willReturn(Optional.of(1L));
        given(refreshTokenGenerator.generate()).willReturn(newRefreshToken);
        given(jwtTokenProperties.refreshTokenTtl()).willReturn(refreshTokenTtl);
        given(accessTokenGenerator.generate(1L)).willReturn(newAccessToken);

        RefreshResult result = refreshTokenService.refresh("old-refresh-token");

        assertThat(result.accessToken()).isEqualTo(newAccessToken);
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        then(refreshTokenRepository).should().save(
                "new-refresh-token-hash",
                1L,
                refreshTokenTtl
        );
    }

    @Test
    void 저장되지_않은_리프레시_토큰은_거절한다() {
        given(refreshTokenGenerator.hash("invalid-refresh-token"))
                .willReturn("invalid-refresh-token-hash");
        given(refreshTokenRepository.consume("invalid-refresh-token-hash"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.refresh("invalid-refresh-token"))
                .isInstanceOf(PickUpException.class)
                .hasMessage("유효하지 않은 리프레시 토큰입니다.");

        then(accessTokenGenerator).shouldHaveNoInteractions();
    }
}
