package com.ootd.pickup.auth.token;

public class InvalidAdminAccessTokenException extends RuntimeException {

  public InvalidAdminAccessTokenException() {}

  public InvalidAdminAccessTokenException(Throwable cause) {
    super(cause);
  }
}
