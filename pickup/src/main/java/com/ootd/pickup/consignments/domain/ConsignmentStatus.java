package com.ootd.pickup.consignments.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;
import java.util.List;

public enum ConsignmentStatus {
  // 위탁 등록 완료, 경매 등록 가능(신규 등록 또는 유찰 후 재등록)
  REGISTERABLE,
  // 경매 신청부터 종료 전까지(시작 대기 중이든 진행 중이든)
  IN_AUCTION,
  // 낙찰되어 판매 완료
  SOLD;

  /** 경매가 예정/진행 중이라 셀러가 관리를 계속해야 하는 상태. 회원 탈퇴 가능 여부 판단 등에 쓰인다. */
  private static final List<ConsignmentStatus> ACTIVE_IN_AUCTION_STATUSES =
      List.of(AUCTION_SCHEDULED, AUCTION_ONGOING);

  public boolean isModifiable() {
    return this == REGISTERABLE;
  }

  public boolean isDeletable() {
    return isModifiable();
  }

  public static List<ConsignmentStatus> activeInAuctionStatuses() {
    return ACTIVE_IN_AUCTION_STATUSES;
  }

  public static ConsignmentStatus from(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }

    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(status))
        .findFirst()
        .orElseThrow(() -> new PickUpException(INVALID_CONSIGNMENT_STATUS));
  }
}
