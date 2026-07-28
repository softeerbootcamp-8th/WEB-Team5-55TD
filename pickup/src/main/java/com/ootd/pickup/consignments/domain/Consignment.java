package com.ootd.pickup.consignments.domain;

import com.ootd.pickup.cards.domain.Card;
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

    // 유저 구현 후 Join 필요
    @Column(name = "seller_member_id", nullable = false)
    private Long sellerMemberId;

    @Column(name = "major_defect")
    private String majorDefect;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConsignmentStatus status;

    @Builder
    public Consignment(
            Card card,
            Long sellerMemberId,
            String majorDefect,
            ConsignmentStatus status
    ) {
        this.card = card;
        this.sellerMemberId = sellerMemberId;
        this.majorDefect = majorDefect;
        this.status = status;
    }
}
