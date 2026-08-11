package com.ootd.pickup.auction.domain;

import com.ootd.pickup.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "watch",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_watch_auction_member",
            columnNames = {"auction_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Watch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "watch_id", nullable = false)
  private Long watchId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "auction_id", nullable = false)
  private Auction auction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Builder
  public Watch(Auction auction, Member member) {
    this.auction = auction;
    this.member = member;
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }
}
