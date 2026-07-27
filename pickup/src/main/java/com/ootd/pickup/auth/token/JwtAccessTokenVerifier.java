package com.ootd.pickup.auth.token;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.time.Instant;

@RequiredArgsConstructor
public class JwtAccessTokenVerifier implements AccessTokenVerifier {
    private final String issuer;
    private final SecretKey signingKey;

    @Override
    public Authentication verify(String accessToken) {
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
            String sessionId = claims.get(JwtTokenClaims.SESSION_ID, String.class);
            String tokenId = claims.getId();
            Instant expiresAt = claims.getExpiration().toInstant();
            return new Authentication(memberId, sessionId, tokenId, expiresAt);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAccessTokenException();
        }
    }

    private static final class InvalidAccessTokenException extends PickUpException {
        private InvalidAccessTokenException() {
            super(ExceptionCode.INVALID_ACCESS_TOKEN);
        }
    }
}
