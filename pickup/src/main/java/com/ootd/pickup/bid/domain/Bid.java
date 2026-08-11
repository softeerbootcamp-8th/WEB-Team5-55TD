package com.ootd.pickup.bid.domain;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter(AccessLevel.NONE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "bid_id", nullable = false)
  @Getter
  private Long bidId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "auction_id", nullable = false)
  @Getter
  private Auction auction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  @Getter
  private Member member;

  @Column(name = "bid_price", nullable = false)
  @Getter
  private Long bidPrice;

  @Column(name = "created_at", nullable = false)
  @Getter
  private LocalDateTime createdAt;

  private Bid(Auction auction, Member member, Long bidPrice) {
    this.auction = auction;
    this.member = member;
    this.bidPrice = bidPrice;
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public static Bid create(Auction auction, Member member, Long bidPrice) {
    return new Bid(auction, member, bidPrice);
  }

  /**
   * 이 입찰의 상태를 계산한다. 저장된 컬럼이 아니라 {@link Auction#getWinningBidId()}/{@link
   * Auction#getAuctionStatus()}로부터 그때그때 판단한다 — "누가 최고인지"의 유일한 근거는 Auction이고, Bid가 별도로 자신의 상태를 들고
   * 있으면 둘이 어긋날 수 있다.
   */
  public BidStatus getBidStatus() {
    if (!bidId.equals(auction.getWinningBidId())) {
      return BidStatus.OUTBID;
    }
    return auction.getAuctionStatus() == AuctionStatus.WON ? BidStatus.WON : BidStatus.HIGHEST;
  }
}
