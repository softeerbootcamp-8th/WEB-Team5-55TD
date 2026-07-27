package com.ootd.pickup.auth.token;

import java.security.SecureRandom;
import java.util.Base64;

public class SecureRefreshTokenGenerator implements RefreshTokenGenerator {
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom;

    public SecureRefreshTokenGenerator() {
        this(new SecureRandom());
    }

    SecureRefreshTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
