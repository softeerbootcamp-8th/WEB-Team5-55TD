package com.ootd.pickup.auth.token;

import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class JwtAccessTokenGenerator implements AccessTokenGenerator {
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final String issuer;
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtAccessTokenGenerator(String issuer, SecretKey signingKey, Duration accessTokenTtl) {
        this.issuer = issuer;
        this.signingKey = signingKey;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public GeneratedAccessToken generate(Long memberId, String sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(memberId.toString())
                .id(tokenId)
                .claim("sid", sessionId)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new GeneratedAccessToken(token, tokenId, sessionId, expiresAt);
    }
}
