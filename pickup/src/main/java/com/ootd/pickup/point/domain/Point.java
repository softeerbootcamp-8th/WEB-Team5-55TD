package com.ootd.pickup.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Point {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long pointId;

  @Column(nullable = false, unique = true)
  private Long memberId;

  @Column(nullable = false)
  private long balance;

  @Column(nullable = false)
  private long reservedBalance;

  public static Point create(Long memberId) {
    Point point = new Point();
    point.memberId = memberId;
    point.balance = 0;
    point.reservedBalance = 0;
    return point;
  }

  public void increaseBalance(long amount) {
    validateAmount(amount);
    this.balance = Math.addExact(this.balance, amount);
  }

  public void decreaseBalance(long amount) {
    validateAmount(amount);
    if (amount > getAvailableBalance()) {
      throw new IllegalStateException("사용 가능한 포인트가 부족합니다 - amount=" + amount);
    }
    this.balance -= amount;
  }

  public void reserve(long amount) {
    validateAmount(amount);
    if (amount > getAvailableBalance()) {
      throw new IllegalStateException("사용 가능한 포인트가 부족합니다 - amount=" + amount);
    }
    this.reservedBalance = Math.addExact(this.reservedBalance, amount);
  }

  public void release(long amount) {
    validateAmount(amount);
    if (amount > this.reservedBalance) {
      throw new IllegalStateException("예약된 포인트보다 큰 금액을 해제할 수 없습니다 - amount=" + amount);
    }
    this.reservedBalance -= amount;
  }

  public void capture(long amount) {
    validateAmount(amount);
    if (amount > this.reservedBalance || amount > this.balance) {
      throw new IllegalStateException("예약된 포인트를 차감할 수 없습니다 - amount=" + amount);
    }
    this.reservedBalance -= amount;
    this.balance -= amount;
  }

  public long getAvailableBalance() {
    return this.balance - this.reservedBalance;
  }

  private void validateAmount(long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("포인트 변경 금액은 0보다 커야 합니다 - amount=" + amount);
    }
  }
}
