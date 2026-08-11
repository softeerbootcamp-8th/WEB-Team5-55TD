package com.ootd.pickup.consignments.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;

public enum ConsignmentStatus {
  // 위탁 등록 완료, 경매 등록 가능(신규 등록 또는 유찰 후 재등록)
  REGISTERABLE,
  // 경매 신청부터 종료 전까지(시작 대기 중이든 진행 중이든)
  IN_AUCTION,
  // 낙찰되어 판매 완료
  SOLD;

  public boolean isModifiable() {
    return this == REGISTERABLE;
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
