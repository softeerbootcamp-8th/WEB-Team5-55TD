package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;

public enum AuctionSort {
  POPULAR,
  PRICE_ASC,
  PRICE_DESC,
  ENDING_SOON,
  STARTING_SOON,
  RECENT;

  public static AuctionSort from(String sort) {
    if (sort == null || sort.isBlank()) {
      return POPULAR;
    }

    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(sort))
        .findFirst()
        .orElseThrow(() -> new PickUpException(INVALID_AUCTION_SORT));
  }
}
