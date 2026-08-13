package com.ootd.pickup.auth.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponseBody;
import com.ootd.pickup.auth.dto.RefreshResponseBody;
import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.RefreshToken;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final MemberRepository memberRepository;
  private final AccessTokenGenerator accessTokenGenerator;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProperties jwtTokenProperties;
  private final ImageUrlResolver imageUrlResolver;

  public LoginResponse login(LoginRequest loginRequest) {
    Member member =
        memberRepository
            .findByLoginId(loginRequest.loginId())
            .orElseThrow(() -> new PickUpException(INVALID_PASSWORD));

    if (!member.isPasswordMatched(loginRequest.password())) {
      throw new PickUpException(INVALID_PASSWORD);
    }

    return issueLogin(member);
  }

  public LoginResponse issueLogin(Member member) {
    AccessToken accessToken = accessTokenGenerator.generate(member.getMemberId());
    RefreshToken refreshToken = refreshTokenGenerator.generate();

    refreshTokenRepository.save(
        refreshToken.hash(), member.getMemberId(), jwtTokenProperties.refreshTokenTtl());

    LoginResponseBody body =
        new LoginResponseBody(
            member.getMemberId(),
            member.getLoginId(),
            member.getNickname(),
            member.getResolvedProfileImageUrl(imageUrlResolver));

    log.info("로그인했습니다 - memberId={}", member.getMemberId());
    return new LoginResponse(body, accessToken, refreshToken.value());
  }

  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }

    String tokenHash = refreshTokenGenerator.hash(refreshToken);
    refreshTokenRepository.delete(tokenHash);
    log.info("로그아웃했습니다");
  }

  /** 회원이 가진 모든 기기의 리프레시 토큰을 회수한다. 탈퇴 후 재로그인을 막기 위해 쓴다. */
  public void revokeAllRefreshTokens(Long memberId) {
    refreshTokenRepository.deleteByMemberId(memberId);
  }

  public RefreshResponse refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new PickUpException(INVALID_REFRESH_TOKEN);
    }

    String oldTokenHash = refreshTokenGenerator.hash(refreshToken);
    Long memberId =
        refreshTokenRepository
            .consume(oldTokenHash)
            .orElseThrow(() -> new PickUpException(INVALID_REFRESH_TOKEN));

    RefreshToken newRefreshToken = refreshTokenGenerator.generate();
    refreshTokenRepository.save(
        newRefreshToken.hash(), memberId, jwtTokenProperties.refreshTokenTtl());

    AccessToken newAccessToken = accessTokenGenerator.generate(memberId);
    RefreshResponseBody body = new RefreshResponseBody(newAccessToken.expiresAt());
    log.info("토큰을 재발급했습니다 - memberId={}", memberId);
    return new RefreshResponse(body, newAccessToken, newRefreshToken.value());
  }
}
