package com.ootd.pickup.auth.token;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ootd.pickup.auth.token.jwt.JwtAccessTokenGenerator;
import com.ootd.pickup.auth.token.jwt.JwtAccessTokenVerifier;
import com.ootd.pickup.global.auth.Authentication;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAccessTokenTest {
    private static final String ISSUER = "pickup-test";
    private final SecretKey signingKey = Keys.hmacShaKeyFor(new byte[32]);
    private JwtAccessTokenGenerator generator;
    private JwtAccessTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        generator = new JwtAccessTokenGenerator(ISSUER, signingKey, Duration.ofMinutes(15));
        verifier = new JwtAccessTokenVerifier(ISSUER, signingKey);
    }

    @Test
    void 액세스_토큰을_생성하고_인증한다() {
        AccessToken generatedToken = generator.generate(1L);

        Authentication authentication = verifier.verify(generatedToken.value());

        assertThat(authentication.memberId()).isEqualTo(1L);
    }

    @Test
    void 서명이_다른_액세스_토큰은_인증하지_않는다() {
        AccessToken generatedToken = generator.generate(1L);
        byte[] anotherKeyBytes = new byte[32];
        anotherKeyBytes[0] = 1;
        SecretKey anotherKey = Keys.hmacShaKeyFor(anotherKeyBytes);
        assertThatThrownBy(() -> new JwtAccessTokenVerifier(ISSUER, anotherKey).verify(generatedToken.value()))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 빈_액세스_토큰은_인증하지_않는다() {
        assertThatThrownBy(() -> verifier.verify(" "))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 액세스_토큰이_아닌_JWT는_인증하지_않는다() {
        String refreshToken = createToken("1", "refresh", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(refreshToken))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 회원_ID가_숫자가_아니면_인증하지_않는다() {
        String accessToken = createToken("member", "access", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(accessToken))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 만료된_액세스_토큰은_인증하지_않는다() {
        String accessToken = createToken("1", "access", Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> verifier.verify(accessToken))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 리프레시_토큰은_서로_다른_보안_문자열로_생성한다() {
        RefreshTokenGenerator refreshTokenGenerator = new RefreshTokenGenerator();

        RefreshToken firstToken = refreshTokenGenerator.generate();
        RefreshToken secondToken = refreshTokenGenerator.generate();

        assertThat(firstToken.value()).isNotEqualTo(secondToken.value());
        assertThat(Base64.getUrlDecoder().decode(firstToken.value())).hasSize(32);
        assertThat(refreshTokenGenerator.hash(firstToken.value()))
            .isEqualTo(firstToken.hash());
    }

    private String createToken(String subject, String tokenType, Instant expiresAt) {
        return Jwts.builder()
            .issuer(ISSUER)
            .subject(subject)
            .claim("token_type", tokenType)
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();
    }
}
