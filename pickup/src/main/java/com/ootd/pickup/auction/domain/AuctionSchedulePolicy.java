package com.ootd.pickup.auction.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/** 경매 기본 진행 기간과 마감 연장 규칙을 한곳에서 관리한다. */
public final class AuctionSchedulePolicy {

  public static final Duration DEFAULT_DURATION = Duration.ofDays(7);
  public static final Duration SOFT_CLOSE_WINDOW = Duration.ofMinutes(5);

  private AuctionSchedulePolicy() {}

  public static LocalDateTime initialEndAt(LocalDateTime startedAt) {
    return startedAt.plus(DEFAULT_DURATION);
  }
}
