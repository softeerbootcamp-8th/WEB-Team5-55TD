package com.ootd.pickup.auth.token.jwt;

import java.time.Instant;

import javax.crypto.SecretKey;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;

import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.auth.token.InvalidAccessTokenException;
import com.ootd.pickup.global.auth.Authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;

@RequiredArgsConstructor
public class JwtAccessTokenVerifier implements AccessTokenVerifier {
    private final String issuer;
    private final SecretKey signingKey;

    @Override
    public Authentication verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new InvalidAccessTokenException();
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

            String tokenType = claims.get(JwtTokenClaims.TOKEN_TYPE, String.class);
            if (!JwtTokenClaims.ACCESS_TOKEN_TYPE.equals(tokenType)) {
                throw new InvalidAccessTokenException();
            }

            Long memberId = Long.valueOf(claims.getSubject());
            return new Authentication(memberId);
        } catch (JwtException | NumberFormatException exception) {
            throw new InvalidAccessTokenException(exception);
        }
    }
}
