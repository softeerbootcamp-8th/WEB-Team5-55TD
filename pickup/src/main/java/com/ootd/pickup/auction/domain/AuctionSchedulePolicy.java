package com.ootd.pickup.auction.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 경매 시작 슬롯, 기본 진행 기간, 마감 연장 규칙을 한곳에서 관리한다. */
public final class AuctionSchedulePolicy {

  public static final LocalTime START_TIME = LocalTime.of(21, 0);
  public static final Duration DEFAULT_DURATION = Duration.ofDays(7);
  public static final Duration SOFT_CLOSE_WINDOW = Duration.ofMinutes(5);

  private AuctionSchedulePolicy() {}

  /** 판매자가 요청한 날짜를 당일 21시(KST 운영 슬롯)로 확정한다. */
  public static LocalDateTime confirmStartAt(LocalDateTime requestedStartAt) {
    return requestedStartAt.toLocalDate().atTime(START_TIME);
  }

  public static LocalDateTime initialEndAt(LocalDateTime confirmedStartAt) {
    return confirmedStartAt.plus(DEFAULT_DURATION);
  }
}
