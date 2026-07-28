package com.ootd.pickup.auth.token;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@RequiredArgsConstructor
public class JwtAccessTokenGenerator implements AccessTokenGenerator {
    private final String issuer;
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    @Override
    public GeneratedAccessToken generate(Long memberId, String sessionId) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(memberId.toString())
                .id(tokenId)
                .claim(JwtTokenClaims.SESSION_ID, sessionId)
                .claim(JwtTokenClaims.TOKEN_TYPE, JwtTokenClaims.ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new GeneratedAccessToken(token, tokenId, sessionId, expiresAt);
    }
}
