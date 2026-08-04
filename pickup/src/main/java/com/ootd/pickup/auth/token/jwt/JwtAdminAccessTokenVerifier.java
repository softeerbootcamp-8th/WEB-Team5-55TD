package com.ootd.pickup.auth.token.jwt;

import com.ootd.pickup.auth.token.AdminAccessTokenVerifier;
import com.ootd.pickup.auth.token.InvalidAdminAccessTokenException;
import com.ootd.pickup.global.auth.AdminAuthentication;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAdminAccessTokenVerifier implements AdminAccessTokenVerifier {
  private final String issuer;
  private final SecretKey signingKey;

  @Override
  public AdminAuthentication verify(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      throw new InvalidAdminAccessTokenException();
    }

    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(signingKey)
              .requireIssuer(issuer)
              .build()
              .parseSignedClaims(accessToken)
              .getPayload();

      String tokenType = claims.get(JwtTokenClaims.TOKEN_TYPE, String.class);
      if (!JwtTokenClaims.ADMIN_ACCESS_TOKEN_TYPE.equals(tokenType)) {
        throw new InvalidAdminAccessTokenException();
      }

      Long adminId = Long.valueOf(claims.getSubject());
      return new AdminAuthentication(adminId);
    } catch (JwtException | NumberFormatException exception) {
      throw new InvalidAdminAccessTokenException(exception);
    }
  }
}
