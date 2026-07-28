package com.ootd.pickup.auth.token.jwt;

import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.AccessToken;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAccessTokenGenerator implements AccessTokenGenerator {
    private final String issuer;
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    @Override
    public AccessToken generate(Long memberId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String token = Jwts.builder()
            .issuer(issuer)
            .subject(memberId.toString())
            .claim(JwtTokenClaims.TOKEN_TYPE, JwtTokenClaims.ACCESS_TOKEN_TYPE)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();

        return new AccessToken(token, expiresAt);
    }
}
