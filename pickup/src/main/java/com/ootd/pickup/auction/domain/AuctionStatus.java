package com.ootd.pickup.auction.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;

public enum AuctionStatus {
  // 경매 시작 대기 중
  SCHEDULED,
  // 경매 진행 중
  ONGOING,
  // 낙찰되어 종료
  WON,
  // 유찰되어 종료
  PASSED;

  public static AuctionStatus from(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }

    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(status))
        .findFirst()
        .orElseThrow(() -> new PickUpException(INVALID_AUCTION_STATUS));
  }
}
