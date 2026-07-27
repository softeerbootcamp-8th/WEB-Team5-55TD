package com.ootd.pickup.auth.token;

import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        GeneratedAccessToken generatedToken = generator.generate(1L, "session-1");

        AuthenticatedToken authenticatedToken = verifier.verify(generatedToken.value());

        assertThat(authenticatedToken.memberId()).isEqualTo(1L);
        assertThat(authenticatedToken.sessionId()).isEqualTo("session-1");
        assertThat(authenticatedToken.tokenId()).isEqualTo(generatedToken.tokenId());
        assertThat(authenticatedToken.expiresAt()).isEqualTo(generatedToken.expiresAt());
    }

    @Test
    void 서명이_다른_액세스_토큰은_인증하지_않는다() {
        GeneratedAccessToken generatedToken = generator.generate(1L, "session-1");
        byte[] anotherKeyBytes = new byte[32];
        anotherKeyBytes[0] = 1;
        SecretKey anotherKey = Keys.hmacShaKeyFor(anotherKeyBytes);
        assertThatThrownBy(() -> new JwtAccessTokenVerifier(ISSUER, anotherKey).verify(generatedToken.value()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 리프레시_토큰은_서로_다른_보안_문자열로_생성한다() {
        SecureRefreshTokenGenerator refreshTokenGenerator = new SecureRefreshTokenGenerator();

        String firstToken = refreshTokenGenerator.generate();
        String secondToken = refreshTokenGenerator.generate();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(Base64.getUrlDecoder().decode(firstToken)).hasSize(32);
    }
}
