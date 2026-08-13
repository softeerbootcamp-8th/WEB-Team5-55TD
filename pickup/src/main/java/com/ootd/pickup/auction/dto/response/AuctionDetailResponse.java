package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.CardState;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.dto.response.ConsignmentImageResponse;
import com.ootd.pickup.images.service.ImageUrlResolver;
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
    Long sellerId,
    String sellerNickname,
    CertificateResponse certificate,
    List<ConsignmentImageResponse> images,
    CardState cardState,
    String majorDefect,
    Long bidIncrement,
    Long nextMinBid,
    Long recommendedBid,
    // 조회자 본인이 이 경매의 낙찰자인지. 비로그인 상태거나 낙찰자가 아니면 false.
    boolean myBidWon) {

  public static AuctionDetailResponse of(
      Auction auction,
      Certificate certificate,
      List<ConsignmentImage> images,
      long watchCount,
      boolean watched,
      Long currentPrice,
      ImageUrlResolver imageUrlResolver,
      boolean myBidWon) {
    Consignment consignment = auction.getConsignment();

    return new AuctionDetailResponse(
        auction.getAuctionId(),
        consignment.getConsignmentId(),
        GetCardDetailResponse.from(consignment.getCard()),
        certificate.getGradeDisplay(),
        auction.getAuctionStatus(),
        auction.getStartingPrice(),
        currentPrice,
        auction.getStartedAt(),
        auction.getEndedAt(),
        auction.getRemainingSeconds(),
        watchCount,
        watched,
        resolveThumbnailUrl(images, imageUrlResolver),
        consignment.getSellerMember().getMemberId(),
        consignment.getSellerMember().getNickname(),
        CertificateResponse.from(certificate),
        images.stream()
            .map(image -> ConsignmentImageResponse.from(image, imageUrlResolver))
            .toList(),
        consignment.getCardState(),
        consignment.getMajorDefect(),
        auction.getBidIncrement(),
        nextMinBid(auction, currentPrice),
        // TODO: 입찰 이력 기반 추천 입찰가 도입 전까지 미제공
        null,
        myBidWon);
  }

  private static String resolveThumbnailUrl(
      List<ConsignmentImage> images, ImageUrlResolver imageUrlResolver) {
    return images.isEmpty() ? null : imageUrlResolver.resolve(images.getFirst().getObjectKey());
  }

  private static Long nextMinBid(Auction auction, Long currentPrice) {
    // 입찰 이력이 없으면 시작가부터, 있으면 현재가 + 최소 입찰 단위부터 입찰 가능하다.
    // startingPrice에 상한이 없어 이론상 Long 덧셈이 넘칠 수 있다. 조용히 음수로 랩어라운드되어
    // 잘못된 값을 200으로 내려보내는 것보다는, addExact로 예외를 던져 500 + Slack 알림으로
    // 드러나게 하는 편이 안전하다.
    return currentPrice == null
        ? auction.getStartingPrice()
        : Math.addExact(currentPrice, auction.getBidIncrement());
  }
}
