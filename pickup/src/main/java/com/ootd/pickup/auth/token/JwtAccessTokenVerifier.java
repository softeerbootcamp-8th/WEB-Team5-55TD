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
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

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

            if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                throw new InvalidAccessTokenException();
            }

            String sessionId = claims.get("sid", String.class);
            String tokenId = claims.getId();
            Long memberId = Long.valueOf(claims.getSubject());
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
