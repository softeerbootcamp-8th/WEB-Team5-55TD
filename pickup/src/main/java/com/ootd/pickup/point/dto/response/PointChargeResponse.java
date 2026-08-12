package com.ootd.pickup.point.dto.response;

import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointTransaction;
import java.time.LocalDateTime;

public record PointChargeResponse(
    Long pointTransactionId,
    long chargedAmount,
    long pointBalance,
    long reservedPointBalance,
    long availablePointBalance,
    LocalDateTime createdAt) {

  public static PointChargeResponse fromEntity(PointTransaction transaction, Point point) {
    return new PointChargeResponse(
        transaction.getPointTransactionId(),
        transaction.getAmount(),
        point.getBalance(),
        point.getReservedBalance(),
        point.getAvailableBalance(),
        transaction.getCreatedAt());
  }
}
