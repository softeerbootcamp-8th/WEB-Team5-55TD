package com.ootd.pickup.auth.token.jwt;

import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.auth.token.AdminAccessTokenGenerator;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAdminAccessTokenGenerator implements AdminAccessTokenGenerator {
  private final String issuer;
  private final SecretKey signingKey;
  private final Duration accessTokenTtl;

  @Override
  public AccessToken generate(Long adminId) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(accessTokenTtl);
    String token =
        Jwts.builder()
            .issuer(issuer)
            .subject(adminId.toString())
            .claim(JwtTokenClaims.TOKEN_TYPE, JwtTokenClaims.ADMIN_ACCESS_TOKEN_TYPE)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();

    return new AccessToken(token, expiresAt);
  }
}
