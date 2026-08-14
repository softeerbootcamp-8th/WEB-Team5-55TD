package com.ootd.pickup.global.auth;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 이중 제출 쿠키(double-submit cookie) 방식의 CSRF 토큰을 만든다. 서버가 값을 저장해두지 않고, 로그인 시 쿠키로 내려준 값을 프론트가 헤더에 그대로
 * 되돌려 보내면 그 둘이 일치하는지만 검증한다.
 */
@Component
public class CsrfTokenGenerator {
  private static final int TOKEN_BYTE_LENGTH = 32;

  private final SecureRandom secureRandom = new SecureRandom();

  public String generate() {
    byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(tokenBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
  }
}
