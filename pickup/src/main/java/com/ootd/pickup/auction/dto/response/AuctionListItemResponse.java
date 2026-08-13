package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;
import java.time.LocalDateTime;

public record AuctionListItemResponse(
    Long auctionId,
    Long consignmentId,
    String title,
    GetCardDetailResponse card,
    String grade,
    AuctionStatus auctionStatus,
    Long startingPrice,
    Long currentPrice,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Long remainingSeconds,
    long watchCount,
    boolean watched,
    String thumbnailUrl) {

  public static AuctionListItemResponse of(
      Auction auction,
      Certificate certificate,
      String thumbnailUrl,
      long watchCount,
      boolean watched,
      Long currentPrice) {
    return new AuctionListItemResponse(
        auction.getAuctionId(),
        auction.getConsignment().getConsignmentId(),
        auction.getTitle(),
        GetCardDetailResponse.from(auction.getConsignment().getCard()),
        certificate != null ? certificate.getGradeDisplay() : null,
        auction.getAuctionStatus(),
        auction.getStartingPrice(),
        currentPrice,
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getRemainingSeconds(),
        watchCount,
        watched,
        thumbnailUrl);
  }
}
