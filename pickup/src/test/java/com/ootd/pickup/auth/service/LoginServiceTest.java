package com.ootd.pickup.auth.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.GeneratedAccessToken;
import com.ootd.pickup.auth.token.GeneratedRefreshToken;
import com.ootd.pickup.auth.token.JwtTokenProperties;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AccessTokenGenerator accessTokenGenerator;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProperties jwtTokenProperties;

    @InjectMocks
    private LoginService loginService;

    @Test
    void 로그인에_성공하면_액세스_토큰과_리프레시_토큰을_발급한다() {
        LoginRequest request = new LoginRequest("pickup-user", "password1234");
        Member member = createMember(request.loginId(), request.password());
        GeneratedAccessToken accessToken = new GeneratedAccessToken(
                "access-token",
                Instant.now().plusSeconds(900)
        );
        GeneratedRefreshToken refreshToken = new GeneratedRefreshToken(
                "refresh-token",
                "refresh-token-hash"
        );
        Duration refreshTokenTtl = Duration.ofDays(14);
        given(memberRepository.findByLoginId(request.loginId())).willReturn(Optional.of(member));
        given(accessTokenGenerator.generate(member.getMemberId())).willReturn(accessToken);
        given(refreshTokenGenerator.generate()).willReturn(refreshToken);
        given(jwtTokenProperties.refreshTokenTtl()).willReturn(refreshTokenTtl);

        LoginResult result = loginService.login(request);

        assertThat(result.response().memberId()).isEqualTo(member.getMemberId());
        assertThat(result.accessToken()).isEqualTo(accessToken);
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        then(accessTokenGenerator).should().generate(member.getMemberId());
        then(refreshTokenGenerator).should().generate();
        then(refreshTokenRepository).should().save(
                "refresh-token-hash",
                member.getMemberId(),
                refreshTokenTtl
        );
    }

    @Test
    void 존재하지_않는_아이디로_로그인하면_토큰을_발급하지_않는다() {
        LoginRequest request = new LoginRequest("unknown-user", "password1234");
        given(memberRepository.findByLoginId(request.loginId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(request))
                .isInstanceOf(PickUpException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");

        then(accessTokenGenerator).shouldHaveNoInteractions();
        then(refreshTokenGenerator).shouldHaveNoInteractions();
    }

    @Test
    void 비밀번호가_일치하지_않으면_토큰을_발급하지_않는다() {
        Member member = createMember("pickup-user", "password1234");
        LoginRequest request = new LoginRequest(member.getLoginId(), "wrong-password");
        given(memberRepository.findByLoginId(request.loginId())).willReturn(Optional.of(member));

        assertThatThrownBy(() -> loginService.login(request))
                .isInstanceOf(PickUpException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");

        then(accessTokenGenerator).shouldHaveNoInteractions();
        then(refreshTokenGenerator).shouldHaveNoInteractions();
    }

    private Member createMember(String loginId, String rawPassword) {
        String passwordHash = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());
        Member member = Member.create(loginId, passwordHash, "픽업회원");
        setMemberId(member, 1L);
        return member;
    }

    private void setMemberId(Member member, Long memberId) {
        try {
            Field memberIdField = Member.class.getDeclaredField("memberId");
            memberIdField.setAccessible(true);
            memberIdField.set(member, memberId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
