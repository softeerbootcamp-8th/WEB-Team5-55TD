package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.util.CursorCodec;

public record SalesCursor(long endedAtEpochMillis, long auctionId) {

  public static String encode(long endedAtEpochMillis, long auctionId) {
    return CursorCodec.encode(endedAtEpochMillis, auctionId);
  }

  public static SalesCursor decode(String cursor) {
    return CursorCodec.decode(
        cursor,
        parts -> {
          if (parts.length != 2) {
            throw new PickUpException(INVALID_CURSOR);
          }
          return new SalesCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        });
  }
}
