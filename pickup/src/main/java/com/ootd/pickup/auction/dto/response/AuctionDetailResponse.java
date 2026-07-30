package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.dto.response.ConsignmentImageResponse;
import java.time.LocalDateTime;
import java.util.List;

public record AuctionDetailResponse(
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
    String thumbnailUrl,
    String sellerNickname,
    CertificateResponse certificate,
    List<ConsignmentImageResponse> images,
    String cardState,
    String majorDefect,
    Long bidIncrement,
    Long nextMinBid,
    Long recommendedBid) {

  public static AuctionDetailResponse of(
      Auction auction,
      Certificate certificate,
      List<ConsignmentImage> images,
      long watchCount,
      boolean watched) {
    Consignment consignment = auction.getConsignment();
    // TODO: Bid 도메인 도입 후 현재 최고 입찰가로 교체
    Long currentPrice = null;

    return new AuctionDetailResponse(
        auction.getAuctionId(),
        consignment.getConsignmentId(),
        GetCardDetailResponse.from(consignment.getCard()),
        certificate.getCertificationBody().name() + " " + certificate.getGrade().getScore(),
        auction.getAuctionStatus(),
        auction.getStartingPrice(),
        currentPrice,
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getRemainingSeconds(),
        watchCount,
        watched,
        resolveThumbnailUrl(images),
        consignment.getSellerMember().getNickname(),
        CertificateResponse.from(certificate),
        images.stream().map(ConsignmentImageResponse::from).toList(),
        certificate.getGrade().getDisplayName(),
        consignment.getMajorDefect(),
        auction.getBidIncrement(),
        nextMinBid(auction, currentPrice),
        // TODO: 입찰 이력 기반 추천 입찰가 도입 전까지 미제공
        null);
  }

  private static String resolveThumbnailUrl(List<ConsignmentImage> images) {
    return images.isEmpty() ? null : images.getFirst().getImageUrl();
  }

  private static Long nextMinBid(Auction auction, Long currentPrice) {
    // 입찰 이력이 없으면 시작가부터, 있으면 현재가 + 최소 입찰 단위부터 입찰 가능하다.
    return currentPrice == null
        ? auction.getStartingPrice()
        : currentPrice + auction.getBidIncrement();
  }
}
