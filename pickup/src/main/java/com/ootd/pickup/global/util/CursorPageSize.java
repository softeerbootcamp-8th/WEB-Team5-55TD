package com.ootd.pickup.global.util;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;

public final class CursorPageSize {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  private CursorPageSize() {}

  public static int resolve(Integer size) {
    if (size == null) {
      return DEFAULT_SIZE;
    }
    if (size < 1) {
      throw new PickUpException(ILLEGAL_ARGUMENT);
    }
    return Math.min(size, MAX_SIZE);
  }
}
