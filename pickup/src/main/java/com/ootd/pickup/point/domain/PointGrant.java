package com.ootd.pickup.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointGrant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long pointGrantId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "admin_id", nullable = false)
  private Long adminId;

  @Column(nullable = false)
  private long amount;

  @Column private String reason;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public static PointGrant create(Long memberId, Long adminId, long amount, String reason) {
    PointGrant pointGrant = new PointGrant();
    pointGrant.memberId = memberId;
    pointGrant.adminId = adminId;
    pointGrant.amount = amount;
    pointGrant.reason = reason;
    pointGrant.createdAt = LocalDateTime.now();
    return pointGrant;
  }
}
