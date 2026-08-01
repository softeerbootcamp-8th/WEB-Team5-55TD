package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.util.StringUtils;

public record SalesCursor(long endedAtEpochMillis, long auctionId) {

  private static final String DELIMITER = "\\|";

  public static String encode(long endedAtEpochMillis, long auctionId) {
    String raw = endedAtEpochMillis + "|" + auctionId;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public static SalesCursor decode(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }

    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split(DELIMITER);
      if (parts.length != 2) {
        throw new PickUpException(INVALID_CURSOR);
      }
      return new SalesCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    } catch (PickUpException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new PickUpException(INVALID_CURSOR);
    }
  }
}
