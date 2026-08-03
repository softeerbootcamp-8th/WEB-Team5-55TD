package com.ootd.pickup.global.dto.request;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;

public interface CursorPageRequest {
  Integer size();

  default void validateSize() {
    if (size() == null || size() < 1) {
      throw new PickUpException(INVALID_PAGE_SIZE);
    }
  }
}
