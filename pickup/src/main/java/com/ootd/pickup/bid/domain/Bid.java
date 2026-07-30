package com.ootd.pickup.bid.domain;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "bid_id", nullable = false)
  private Long bidId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "auction_id", nullable = false)
  private Auction auction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "bid_price", nullable = false)
  private Long bidPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "bid_status", nullable = false)
  private BidStatus bidStatus;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private Bid(Auction auction, Member member, Long bidPrice) {
    this.auction = auction;
    this.member = member;
    this.bidPrice = bidPrice;
    this.bidStatus = BidStatus.HIGHEST;
    this.createdAt = LocalDateTime.now();
  }

  public static Bid create(Auction auction, Member member, Long bidPrice) {
    return new Bid(auction, member, bidPrice);
  }

  public void outbid() {
    this.bidStatus = BidStatus.OUTBID;
  }
}
