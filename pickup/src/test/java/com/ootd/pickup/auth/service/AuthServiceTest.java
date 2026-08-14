package com.ootd.pickup.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.repository.AccessTokenDenylistRepository;
import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.RefreshToken;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private MemberRepository memberRepository;

  @Mock private AccessTokenGenerator accessTokenGenerator;

  @Mock private RefreshTokenGenerator refreshTokenGenerator;

  @Mock private RefreshTokenRepository refreshTokenRepository;

  @Mock private AccessTokenDenylistRepository accessTokenDenylistRepository;

  @Mock private LoginAttemptLimiter loginAttemptLimiter;

  @Mock private JwtTokenProperties jwtTokenProperties;

  @Mock private ImageUrlResolver imageUrlResolver;

  @InjectMocks private AuthService authService;

  @Test
  void 로그인에_성공하면_액세스_토큰과_리프레시_토큰을_발급한다() {
    LoginRequest request = new LoginRequest("pickup-user", "password1234");
    Member member = createMember(request.loginId(), request.password());
    AccessToken accessToken = new AccessToken("access-token", Instant.now().plusSeconds(900));
    RefreshToken refreshToken = new RefreshToken("refresh-token", "refresh-token-hash");
    Duration refreshTokenTtl = Duration.ofDays(14);
    given(memberRepository.findByLoginId(request.loginId())).willReturn(Optional.of(member));
    given(accessTokenGenerator.generate(member.getMemberId())).willReturn(accessToken);
    given(refreshTokenGenerator.generate()).willReturn(refreshToken);
    given(jwtTokenProperties.refreshTokenTtl()).willReturn(refreshTokenTtl);

    LoginResponse response = authService.login(request);

    assertThat(response.body().memberId()).isEqualTo(member.getMemberId());
    assertThat(response.accessToken()).isEqualTo(accessToken);
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    then(accessTokenGenerator).should().generate(member.getMemberId());
    then(refreshTokenGenerator).should().generate();
    then(refreshTokenRepository)
        .should()
        .save("refresh-token-hash", member.getMemberId(), refreshTokenTtl);
    then(loginAttemptLimiter).should().checkAllowed(request.loginId());
    then(loginAttemptLimiter).should().reset(request.loginId());
  }

  @Test
  void 로그인_시도_횟수를_초과하면_토큰을_발급하지_않는다() {
    LoginRequest request = new LoginRequest("pickup-user", "password1234");
    willThrow(new PickUpException(ExceptionCode.TOO_MANY_LOGIN_ATTEMPTS))
        .given(loginAttemptLimiter)
        .checkAllowed(request.loginId());

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.TOO_MANY_LOGIN_ATTEMPTS.getMessage());

    then(memberRepository).shouldHaveNoInteractions();
    then(accessTokenGenerator).shouldHaveNoInteractions();
  }

  @Test
  void 비밀번호가_틀리면_시도_제한을_초기화하지_않는다() {
    Member member = createMember("pickup-user", "password1234");
    LoginRequest request = new LoginRequest(member.getLoginId(), "wrong-password");
    given(memberRepository.findByLoginId(request.loginId())).willReturn(Optional.of(member));

    assertThatThrownBy(() -> authService.login(request)).isInstanceOf(PickUpException.class);

    then(loginAttemptLimiter).should().checkAllowed(request.loginId());
    then(loginAttemptLimiter).should(never()).reset(anyString());
  }

  @Test
  void 존재하지_않는_아이디로_로그인하면_토큰을_발급하지_않는다() {
    LoginRequest request = new LoginRequest("unknown-user", "password1234");
    given(memberRepository.findByLoginId(request.loginId())).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(request))
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

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("비밀번호가 일치하지 않습니다.");

    then(accessTokenGenerator).shouldHaveNoInteractions();
    then(refreshTokenGenerator).shouldHaveNoInteractions();
  }

  @Test
  void 리프레시_토큰을_해시로_변환해_삭제한다() {
    given(refreshTokenGenerator.hash("refresh-token")).willReturn("refresh-token-hash");

    authService.logout("refresh-token");

    then(refreshTokenRepository).should().delete("refresh-token-hash");
  }

  @Test
  void 리프레시_토큰이_없으면_삭제하지_않는다() {
    authService.logout(null);

    verifyNoInteractions(refreshTokenGenerator, refreshTokenRepository);
  }

  @Test
  void 리프레시_토큰이_빈_값이면_삭제하지_않는다() {
    authService.logout(" ");

    verifyNoInteractions(refreshTokenGenerator, refreshTokenRepository);
  }

  @Test
  void 유효한_리프레시_토큰을_새_토큰으로_교체한다() {
    Duration refreshTokenTtl = Duration.ofDays(14);
    RefreshToken newRefreshToken = new RefreshToken("new-refresh-token", "new-refresh-token-hash");
    AccessToken newAccessToken =
        new AccessToken("new-access-token", Instant.now().plusSeconds(900));
    given(refreshTokenGenerator.hash("old-refresh-token")).willReturn("old-refresh-token-hash");
    given(refreshTokenRepository.consume("old-refresh-token-hash")).willReturn(Optional.of(1L));
    given(refreshTokenGenerator.generate()).willReturn(newRefreshToken);
    given(jwtTokenProperties.refreshTokenTtl()).willReturn(refreshTokenTtl);
    given(accessTokenGenerator.generate(1L)).willReturn(newAccessToken);

    RefreshResponse response = authService.refresh("old-refresh-token");

    assertThat(response.body().expiresAt()).isEqualTo(newAccessToken.expiresAt());
    assertThat(response.accessToken()).isEqualTo(newAccessToken);
    assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    then(refreshTokenRepository).should().save("new-refresh-token-hash", 1L, refreshTokenTtl);
  }

  @Test
  void 저장되지_않은_리프레시_토큰은_거절한다() {
    given(refreshTokenGenerator.hash("invalid-refresh-token"))
        .willReturn("invalid-refresh-token-hash");
    given(refreshTokenRepository.consume("invalid-refresh-token-hash"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("invalid-refresh-token"))
        .isInstanceOf(PickUpException.class)
        .hasMessage("유효하지 않은 리프레시 토큰입니다.");

    then(accessTokenGenerator).shouldHaveNoInteractions();
  }

  @Test
  void 리프레시_토큰이_없으면_갱신을_거절한다() {
    assertThatThrownBy(() -> authService.refresh(null))
        .isInstanceOf(PickUpException.class)
        .hasMessage("유효하지 않은 리프레시 토큰입니다.");

    assertThatThrownBy(() -> authService.refresh(" "))
        .isInstanceOf(PickUpException.class)
        .hasMessage("유효하지 않은 리프레시 토큰입니다.");

    verifyNoInteractions(refreshTokenGenerator, refreshTokenRepository);
  }

  @Test
  void 탈퇴_처리시_액세스_토큰_남은_만료시간만큼_거부_목록에_올린다() {
    Duration accessTokenTtl = Duration.ofMinutes(15);
    given(jwtTokenProperties.accessTokenTtl()).willReturn(accessTokenTtl);

    authService.denylistAccessTokens(1L);

    then(accessTokenDenylistRepository).should().denylistMember(1L, accessTokenTtl);
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
