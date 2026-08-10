package com.ootd.pickup.point.domain;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.bid.domain.Bid;
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
@Table(name = "point_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointReservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "point_reservation_id", nullable = false)
  private Long pointReservationId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "auction_id", nullable = false)
  private Auction auction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bid_id", nullable = false)
  private Bid bid;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "reservation_status", nullable = false)
  private PointReservationStatus reservationStatus;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static PointReservation create(Auction auction, Bid bid, Member member, long amount) {
    PointReservation reservation = new PointReservation();
    reservation.auction = auction;
    reservation.bid = bid;
    reservation.member = member;
    reservation.amount = amount;
    reservation.reservationStatus = PointReservationStatus.ACTIVE;
    reservation.createdAt = LocalDateTime.now();
    reservation.updatedAt = reservation.createdAt;
    return reservation;
  }

  public void replace(Bid bid, Member member, long amount) {
    requireActive();
    this.bid = bid;
    this.member = member;
    this.amount = amount;
    this.updatedAt = LocalDateTime.now();
  }

  public void release() {
    requireActive();
    this.reservationStatus = PointReservationStatus.RELEASED;
    this.updatedAt = LocalDateTime.now();
  }

  public void capture() {
    requireActive();
    this.reservationStatus = PointReservationStatus.CAPTURED;
    this.updatedAt = LocalDateTime.now();
  }

  private void requireActive() {
    if (this.reservationStatus != PointReservationStatus.ACTIVE) {
      throw new IllegalStateException("활성 포인트 예약만 변경할 수 있습니다.");
    }
  }
}
