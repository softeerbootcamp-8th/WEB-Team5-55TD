package com.ootd.pickup.auction.domain;

import com.ootd.pickup.consignments.domain.Consignment;
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
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "auction_id", nullable = false)
  private Long auctionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "consignment_id", nullable = false)
  private Consignment consignment;

  // 경매 진행 중에는 "현재 최고 입찰", 종료 후에는 "낙찰 입찰"을 가리킨다.
  @Column(name = "winning_bid_id")
  private Long winningBidId;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "ended_at")
  private LocalDateTime endedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "auction_status", nullable = false)
  private AuctionStatus auctionStatus;

  @Column(name = "starting_price", nullable = false)
  private Long startingPrice;

  @Column(name = "reserve_price", nullable = false)
  private Long reservePrice;

  @Column(name = "bid_increment", nullable = false)
  private Long bidIncrement;

  @Column(name = "winning_price")
  private Long winningPrice;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Builder
  public Auction(
      Consignment consignment,
      LocalDateTime startedAt,
      LocalDateTime endedAt,
      AuctionStatus auctionStatus,
      Long startingPrice,
      Long reservePrice,
      Long bidIncrement) {
    this.consignment = consignment;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.auctionStatus = auctionStatus;
    this.startingPrice = startingPrice;
    this.reservePrice = reservePrice;
    this.bidIncrement = bidIncrement;
    this.createdAt = LocalDateTime.now();
  }

  public Long getRemainingSeconds() {
    if (auctionStatus != AuctionStatus.ONGOING || endedAt == null) {
      return null;
    }
    return Math.max(Duration.between(LocalDateTime.now(), endedAt).getSeconds(), 0);
  }

  public void updateWinningBid(Long winningBidId, Long winningPrice) {
    this.winningBidId = winningBidId;
    this.winningPrice = winningPrice;
  }
}
