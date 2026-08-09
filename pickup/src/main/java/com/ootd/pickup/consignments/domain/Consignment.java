package com.ootd.pickup.consignments.domain;

import static com.ootd.pickup.consignments.domain.ConsignmentStatus.*;
import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.global.exception.PickUpException;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "consignment_id", nullable = false)
  private Long consignmentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id", nullable = false)
  private Card card;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_member_id", nullable = false)
  private Member sellerMember;

  @Column(name = "major_defect")
  private String majorDefect;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ConsignmentStatus status;

  @Builder
  public Consignment(Card card, Member sellerMember, String majorDefect, ConsignmentStatus status) {
    this.card = card;
    this.sellerMember = sellerMember;
    this.majorDefect = majorDefect;
    this.status = status;
  }

  public boolean isModifiable() {
    return status.isModifiable();
  }

  public boolean isDeletable() {
    return status.isDeletable();
  }

  public void updateMajorDefect(String majorDefect) {
    this.majorDefect = majorDefect;
  }

  public void scheduleAuction() {
    // PASSED(유찰)도 재등록 가능한 상태다. REGISTERABLE과 같은 경로로 경매를 신청할 수 있어야 한다.
    if (this.status != REGISTERABLE && this.status != PASSED) {
      throw new PickUpException(CONSIGNMENT_NOT_REGISTERABLE);
    }
    this.status = AUCTION_SCHEDULED;
  }
}
