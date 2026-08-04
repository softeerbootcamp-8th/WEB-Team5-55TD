package com.ootd.pickup.auth.token.jwt;

final class JwtTokenClaims {

  static final String TOKEN_TYPE = "token_type";
  static final String ACCESS_TOKEN_TYPE = "access";
  static final String ADMIN_ACCESS_TOKEN_TYPE = "admin_access";
  static final String SESSION_ID = "sid";

  private JwtTokenClaims() {}
}
