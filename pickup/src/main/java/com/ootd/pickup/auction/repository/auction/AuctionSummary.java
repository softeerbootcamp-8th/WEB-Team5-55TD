package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

/**
 * 위탁 상품 하나에 연결된 경매를 요약한 값.
 *
 * <p>{@code ConsignmentStatus}는 신청~진행 중을 {@code IN_AUCTION} 하나로만 표현하므로, "예정"과 "진행 중"을 구분하거나 재신청 가능
 * 여부(유찰 시각)를 판단하려면 연결된 경매의 상태·시각이 필요하다.
 */
public record AuctionSummary(
    Long auctionId,
    String title,
    AuctionStatus auctionStatus,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {}
