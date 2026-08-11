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

  /**
   * 이 입찰을 만들어낸 비동기 입찰 요청의 id. 기존 동기 엔드포인트(POST .../bids)를 통한 입찰은 {@code null}이다.
   *
   * <p>DB 유니크 제약(uk_bid_bid_request_id)과 짝을 이뤄, SQS 재전달로 같은 입찰 요청이 두 번 처리되는 것을 막는다.
   */
  @Column(name = "bid_request_id")
  private Long bidRequestId;

  private Bid(Auction auction, Member member, Long bidPrice, Long bidRequestId) {
    this.auction = auction;
    this.member = member;
    this.bidPrice = bidPrice;
    this.bidStatus = BidStatus.HIGHEST;
    this.createdAt = LocalDateTime.now();
    this.bidRequestId = bidRequestId;
  }

  public static Bid create(Auction auction, Member member, Long bidPrice) {
    return new Bid(auction, member, bidPrice, null);
  }

  public static Bid create(Auction auction, Member member, Long bidPrice, Long bidRequestId) {
    return new Bid(auction, member, bidPrice, bidRequestId);
  }

  public void outbid() {
    this.bidStatus = BidStatus.OUTBID;
  }
}
