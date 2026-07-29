package com.ootd.pickup.auction.domain;

import java.time.LocalDateTime;

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
    @JoinColumn(name = "product_id", nullable = false)
    private Consignment product;

    // Bid 구현 후 FK/Join 필요
    @Column(name = "winning_bid_id")
    private Long winningBidId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "auction_status", nullable = false)
    private AuctionStatus auctionStatus;

    @Column(name = "starting_price", nullable = false)
    private Long startingPrice;

    @Column(name = "minimum_bid_increment", nullable = false)
    private Long minimumBidIncrement;

    @Column(name = "winning_price")
    private Long winningPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Auction(
        Consignment product,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        AuctionStatus auctionStatus,
        Long startingPrice,
        Long minimumBidIncrement
    ) {
        this.product = product;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.auctionStatus = auctionStatus;
        this.startingPrice = startingPrice;
        this.minimumBidIncrement = minimumBidIncrement;
        this.createdAt = LocalDateTime.now();
    }
}
