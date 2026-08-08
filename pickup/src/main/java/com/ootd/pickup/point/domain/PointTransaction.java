package com.ootd.pickup.point.domain;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "point_transaction_id", nullable = false)
  private Long pointTransactionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false)
  private PointTransactionType transactionType;

  @Column(nullable = false)
  private long amount;

  @Column(name = "balance_after", nullable = false)
  private long balanceAfter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "auction_id")
  private Auction auction;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public static PointTransaction create(
      Member member,
      PointTransactionType transactionType,
      long amount,
      long balanceAfter,
      Auction auction,
      String idempotencyKey) {
    if (amount == 0) {
      throw new IllegalArgumentException("포인트 거래 금액은 0일 수 없습니다.");
    }
    PointTransaction transaction = new PointTransaction();
    transaction.member = member;
    transaction.transactionType = transactionType;
    transaction.amount = amount;
    transaction.balanceAfter = balanceAfter;
    transaction.auction = auction;
    transaction.idempotencyKey = idempotencyKey;
    transaction.createdAt = LocalDateTime.now();
    return transaction;
  }
}
