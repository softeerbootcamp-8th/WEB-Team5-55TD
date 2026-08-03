package com.ootd.pickup.auction.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;

public record SaleHistoryItemResponse(
    Long auctionId,
    GetCardDetailResponse card,
    String grade,
    Long winningPrice,
    AuctionStatus resultType) {

  public static SaleHistoryItemResponse of(Auction auction, Certificate certificate) {
    return new SaleHistoryItemResponse(
        auction.getAuctionId(),
        GetCardDetailResponse.from(auction.getConsignment().getCard()),
        certificate != null ? certificate.getGradeDisplay() : null,
        auction.getWinningPrice(),
        auction.getAuctionStatus());
  }
}
