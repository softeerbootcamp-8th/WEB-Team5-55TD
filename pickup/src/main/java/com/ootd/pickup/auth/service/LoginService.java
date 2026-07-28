package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponse;
import com.ootd.pickup.auth.repository.RefreshTokenRepository;
import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.RefreshToken;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import com.ootd.pickup.auth.token.jwt.JwtTokenProperties;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final MemberRepository memberRepository;
    private final AccessTokenGenerator accessTokenGenerator;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProperties jwtTokenProperties;

    public LoginResult login(LoginRequest loginRequest) {
        Member member = memberRepository.findByLoginId(loginRequest.loginId())
                .orElseThrow(() -> new PickUpException(ExceptionCode.INVALID_PASSWORD));

        if (!member.isPasswordMatched(loginRequest.password())) {
            throw new PickUpException(ExceptionCode.INVALID_PASSWORD);
        }

        AccessToken accessToken = accessTokenGenerator.generate(member.getMemberId());
        RefreshToken refreshToken = refreshTokenGenerator.generate();

        refreshTokenRepository.save(
                refreshToken.hash(),
                member.getMemberId(),
                jwtTokenProperties.refreshTokenTtl()
        );

        LoginResponse response = new LoginResponse(
                member.getMemberId(),
                member.getLoginId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );

        return new LoginResult(response, accessToken, refreshToken.value());
    }
}
