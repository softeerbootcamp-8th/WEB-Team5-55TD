package com.ootd.pickup.global.auth;

public class AuthenticationAttributes {

  public static final String COOKIE_NAME = "access-token";
  public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh-token";
  public static final String ATTRIBUTE_NAME = "authentication";

  /** CSRF 이중 제출 쿠키. httpOnly가 아니라 프론트가 읽어 요청 헤더에 그대로 실어 보낼 수 있어야 한다. */
  public static final String CSRF_TOKEN_COOKIE_NAME = "csrf-token";

  public static final String CSRF_TOKEN_HEADER_NAME = "X-CSRF-Token";
}
