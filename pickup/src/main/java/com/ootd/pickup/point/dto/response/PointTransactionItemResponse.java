package com.ootd.pickup.point.dto.response;

import com.ootd.pickup.point.domain.PointTransaction;
import com.ootd.pickup.point.domain.PointTransactionType;
import java.time.LocalDateTime;

public record PointTransactionItemResponse(
    Long pointTransactionId,
    PointTransactionType transactionType,
    long amount,
    long balanceAfter,
    Long auctionId,
    LocalDateTime createdAt) {

  public static PointTransactionItemResponse fromEntity(PointTransaction transaction) {
    return new PointTransactionItemResponse(
        transaction.getPointTransactionId(),
        transaction.getTransactionType(),
        transaction.getAmount(),
        transaction.getBalanceAfter(),
        transaction.getAuction() == null ? null : transaction.getAuction().getAuctionId(),
        transaction.getCreatedAt());
  }
}
