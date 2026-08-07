package com.ootd.pickup.point.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.POINT_BALANCE_INSUFFICIENT;

import com.ootd.pickup.global.exception.PickUpException;
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

  public static Point create(Long memberId) {
    Point point = new Point();
    point.memberId = memberId;
    point.balance = 0;
    return point;
  }

  /** 관리자가 지급/차감(음수)하는 부호 있는 조정. 결과 잔액이 음수가 되면 안 된다(운영 조치 실패로 보고한다). */
  public void adjustBalance(long amount) {
    long adjustedBalance = balance + amount;
    if (adjustedBalance < 0) {
      throw new PickUpException(POINT_BALANCE_INSUFFICIENT);
    }
    balance = adjustedBalance;
  }

  public void increaseBalance(long amount) {
    validateAmount(amount);
    this.balance += amount;
  }

  public void decreaseBalance(long amount) {
    validateAmount(amount);
    this.balance -= amount;
  }

  private void validateAmount(long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("포인트 변경 금액은 0보다 커야 합니다 - amount=" + amount);
    }
  }
}
