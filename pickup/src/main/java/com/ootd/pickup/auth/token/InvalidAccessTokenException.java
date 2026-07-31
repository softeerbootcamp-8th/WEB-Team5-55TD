package com.ootd.pickup.auth.token;

public class InvalidAccessTokenException extends RuntimeException {

  public InvalidAccessTokenException() {}

  public InvalidAccessTokenException(Throwable cause) {
    super(cause);
  }
}
