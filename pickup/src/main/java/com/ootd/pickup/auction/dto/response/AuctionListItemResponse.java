package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;
import java.time.Duration;
import java.time.LocalDateTime;

public record AuctionListItemResponse(
    Long auctionId,
    Long consignmentId,
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
      boolean watched) {
    return new AuctionListItemResponse(
        auction.getAuctionId(),
        auction.getConsignment().getConsignmentId(),
        GetCardDetailResponse.from(auction.getConsignment().getCard()),
        gradeDisplay(certificate),
        auction.getAuctionStatus(),
        auction.getStartingPrice(),
        // TODO: Bid 도메인 도입 후 현재 최고 입찰가로 교체
        null,
        auction.getStartedAt(),
        auction.getEndedAt(),
        remainingSeconds(auction),
        watchCount,
        watched,
        thumbnailUrl);
  }

  private static String gradeDisplay(Certificate certificate) {
    if (certificate == null) {
      return null;
    }
    return certificate.getCertificationBody().name() + " " + certificate.getGrade().getScore();
  }

  private static Long remainingSeconds(Auction auction) {
    if (auction.getAuctionStatus() != AuctionStatus.ONGOING || auction.getEndedAt() == null) {
      return null;
    }
    return Math.max(Duration.between(LocalDateTime.now(), auction.getEndedAt()).getSeconds(), 0);
  }
}
