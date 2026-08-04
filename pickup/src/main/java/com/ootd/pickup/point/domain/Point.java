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

  public void adjustBalance(long amount) {
    long adjustedBalance = balance + amount;
    if (adjustedBalance < 0) {
      throw new PickUpException(POINT_BALANCE_INSUFFICIENT);
    }
    balance = adjustedBalance;
  }
}
