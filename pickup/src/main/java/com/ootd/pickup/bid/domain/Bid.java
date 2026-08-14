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

  /**
   * 이 입찰을 만들어낸 비동기 입찰 요청의 id. 기존 동기 엔드포인트(POST .../bids)를 통한 입찰은 {@code null}이다.
   *
   * <p>DB 유니크 제약(uk_bid_bid_request_id)과 짝을 이뤄, SQS 재전달로 같은 입찰 요청이 두 번 처리되는 것을 막는다.
   */
  @Column(name = "bid_request_id")
  private Long bidRequestId;

  // 입찰 시점의 입찰자 닉네임 스냅샷. 입찰자가 탈퇴해 Member.nickname이 비워져도 과거 입찰의 표시 닉네임을 보존한다.
  @Column(name = "bidder_nickname_snapshot", nullable = false)
  @Getter
  private String bidderNicknameSnapshot;

  private Bid(Auction auction, Member member, Long bidPrice, Long bidRequestId) {
    this.auction = auction;
    this.member = member;
    this.bidPrice = bidPrice;
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    this.bidRequestId = bidRequestId;
    this.bidderNicknameSnapshot = member.getNickname();
  }

  public static Bid create(Auction auction, Member member, Long bidPrice) {
    return new Bid(auction, member, bidPrice, null);
  }

  public static Bid create(Auction auction, Member member, Long bidPrice, Long bidRequestId) {
    return new Bid(auction, member, bidPrice, bidRequestId);
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
