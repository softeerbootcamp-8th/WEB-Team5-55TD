package com.ootd.pickup.bid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입찰 요청 접수 기록.
 *
 * <p>비동기 처리기가 별도 스레드(SQS 소비 스레드)에서 로드하므로 {@code Auction}/{@code Member} 엔티티를 참조하지 않고 식별자만 담는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BidRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "bid_request_id", nullable = false)
  private Long bidRequestId;

  @Column(name = "auction_id", nullable = false)
  private Long auctionId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "bid_price", nullable = false)
  private Long bidPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private BidRequestStatus status;

  @Column(name = "failure_code")
  private String failureCode;

  @Column(name = "failure_message")
  private String failureMessage;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  private BidRequest(Long auctionId, Long memberId, Long bidPrice) {
    this.auctionId = auctionId;
    this.memberId = memberId;
    this.bidPrice = bidPrice;
    this.status = BidRequestStatus.PENDING;
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public static BidRequest create(Long auctionId, Long memberId, Long bidPrice) {
    return new BidRequest(auctionId, memberId, bidPrice);
  }

  public void succeed() {
    this.status = BidRequestStatus.SUCCEEDED;
    this.processedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void fail(String failureCode, String failureMessage) {
    this.status = BidRequestStatus.FAILED;
    this.failureCode = failureCode;
    this.failureMessage = failureMessage;
    this.processedAt = LocalDateTime.now(ZoneOffset.UTC);
  }
}
