package com.ootd.pickup.settlement.domain;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경매 마감 후 낙찰자/판매자에게 귀속되는 정산 원장 한 줄.
 *
 * <p>소비자는 다른 프로세스에서 트랜잭션 밖에 실행되므로, 이 엔티티는 정산 컨슈머가 SQS 메시지 페이로드로 이미 계산해 둔 값을 그대로 저장하는 용도로만 쓴다.
 */
@Entity
@Table(
    name = "settlement",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_settlement_auction_member_type",
            columnNames = {"auction_id", "member_id", "settlement_type"}),
    indexes = @Index(name = "idx_settlement_member_id", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "settlement_id", nullable = false)
  private Long settlementId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "auction_id", nullable = false)
  private Auction auction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(name = "settlement_type", nullable = false)
  private SettlementType settlementType;

  @Column(name = "amount", nullable = false)
  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "settlement_status", nullable = false)
  private SettlementStatus settlementStatus;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private Settlement(Auction auction, Member member, SettlementType settlementType, Long amount) {
    this.auction = auction;
    this.member = member;
    this.settlementType = settlementType;
    this.amount = amount;
    this.settlementStatus = SettlementStatus.COMPLETED;
    this.createdAt = LocalDateTime.now();
  }

  public static Settlement create(
      Auction auction, Member member, SettlementType settlementType, Long amount) {
    return new Settlement(auction, member, settlementType, amount);
  }
}
