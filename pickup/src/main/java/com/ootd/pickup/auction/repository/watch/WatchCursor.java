package com.ootd.pickup.auction.repository.watch;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.util.CursorCodec;

public record WatchCursor(long watchId) {

  public static String encode(long watchId) {
    return CursorCodec.encode(watchId);
  }

  public static Long decode(String cursor) {
    WatchCursor decoded =
        CursorCodec.decode(
            cursor,
            parts -> {
              if (parts.length != 1) {
                throw new PickUpException(INVALID_CURSOR);
              }
              return new WatchCursor(Long.parseLong(parts[0]));
            });
    return decoded != null ? decoded.watchId() : null;
  }
}
