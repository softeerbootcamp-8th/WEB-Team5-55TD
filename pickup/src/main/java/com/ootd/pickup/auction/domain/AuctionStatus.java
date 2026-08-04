package com.ootd.pickup.auction.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;
import java.util.List;

public enum AuctionStatus {
  // 경매 시작 대기 중
  SCHEDULED,
  // 경매 진행 중
  ONGOING,
  // 낙찰되어 종료
  WON,
  // 유찰되어 종료
  PASSED,
  // 관리자에 의해 강제 취소되어 종료
  CANCELLED;

  private static final List<AuctionStatus> TERMINAL_STATUSES = List.of(WON, PASSED);

  public static List<AuctionStatus> terminalStatuses() {
    return TERMINAL_STATUSES;
  }

  public boolean isTerminal() {
    return TERMINAL_STATUSES.contains(this);
  }

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
