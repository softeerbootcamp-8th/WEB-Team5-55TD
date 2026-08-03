package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.util.CursorCodec;
import com.ootd.pickup.global.util.EpochMillis;
import java.time.LocalDateTime;

public record AuctionCursor(AuctionSort sort, long sortValue, long auctionId) {

  public static final LocalDateTime SENTINEL_END_AT = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

  public static String encode(AuctionSort sort, long sortValue, long auctionId) {
    return CursorCodec.encode(sort.name(), sortValue, auctionId);
  }

  public static AuctionCursor decode(String cursor, AuctionSort expectedSort) {
    return CursorCodec.decode(
        cursor,
        parts -> {
          if (parts.length != 3 || !parts[0].equals(expectedSort.name())) {
            throw new PickUpException(INVALID_CURSOR);
          }
          return new AuctionCursor(
              expectedSort, Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        });
  }

  public static long sortValueOf(AuctionSort sort, Auction auction, long watchCount) {
    return switch (sort) {
      case POPULAR -> watchCount;
      case PRICE_ASC, PRICE_DESC -> auction.getStartingPrice();
      case ENDING_SOON ->
          EpochMillis.from(auction.getEndedAt() != null ? auction.getEndedAt() : SENTINEL_END_AT);
      case STARTING_SOON -> EpochMillis.from(auction.getStartedAt());
      case RECENT -> EpochMillis.from(auction.getCreatedAt());
    };
  }
}
