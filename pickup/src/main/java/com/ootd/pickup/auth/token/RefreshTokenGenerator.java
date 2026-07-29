package com.ootd.pickup.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class RefreshTokenGenerator {
  private static final int TOKEN_BYTE_LENGTH = 32;

  private final SecureRandom secureRandom;

  public RefreshTokenGenerator() {
    this(new SecureRandom());
  }

  RefreshTokenGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public RefreshToken generate() {
    byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(tokenBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    return new RefreshToken(token, hash(token));
  }

  public String hash(String refreshToken) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      byte[] digest = messageDigest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
    }
  }
}
