package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @InjectMocks
    private LogoutService logoutService;

    @Test
    void 리프레시_토큰을_해시로_변환해_삭제한다() {
        given(refreshTokenGenerator.hash("refresh-token"))
                .willReturn("refresh-token-hash");

        logoutService.logout("refresh-token");

        then(refreshTokenRepository).should().delete("refresh-token-hash");
    }

    @Test
    void 리프레시_토큰이_없으면_삭제하지_않는다() {
        logoutService.logout(null);

        verifyNoInteractions(refreshTokenGenerator, refreshTokenRepository);
    }

    @Test
    void 리프레시_토큰이_빈_값이면_삭제하지_않는다() {
        logoutService.logout(" ");

        verifyNoInteractions(refreshTokenGenerator, refreshTokenRepository);
    }
}
