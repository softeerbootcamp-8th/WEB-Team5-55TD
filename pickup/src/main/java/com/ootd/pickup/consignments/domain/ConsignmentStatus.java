package com.ootd.pickup.consignments.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;

public enum ConsignmentStatus {
  // 위탁 등록 완료, 경매 등록 가능
  REGISTERABLE,
  // 경매 등록 완료, 시작 대기 중
  AUCTION_SCHEDULED,
  // 경매 진행 중
  AUCTION_ONGOING,
  // 낙찰되어 판매 완료
  WON,
  // 유찰되어 재등록 가능
  PASSED;

  public boolean isModifiable() {
    return this == REGISTERABLE || this == PASSED;
  }

  public boolean isDeletable() {
    return isModifiable();
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
