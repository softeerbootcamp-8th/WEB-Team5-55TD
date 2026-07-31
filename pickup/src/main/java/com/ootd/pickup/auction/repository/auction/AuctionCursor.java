package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.util.EpochMillis;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.util.StringUtils;

public record AuctionCursor(AuctionSort sort, long sortValue, long auctionId) {

  private static final String DELIMITER = "\\|";
  public static final LocalDateTime SENTINEL_END_AT = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

  public static String encode(AuctionSort sort, long sortValue, long auctionId) {
    String raw = sort.name() + "|" + sortValue + "|" + auctionId;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public static AuctionCursor decode(String cursor, AuctionSort expectedSort) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }

    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split(DELIMITER);
      if (parts.length != 3 || !parts[0].equals(expectedSort.name())) {
        throw new PickUpException(INVALID_CURSOR);
      }
      return new AuctionCursor(expectedSort, Long.parseLong(parts[1]), Long.parseLong(parts[2]));
    } catch (PickUpException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new PickUpException(INVALID_CURSOR);
    }
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
