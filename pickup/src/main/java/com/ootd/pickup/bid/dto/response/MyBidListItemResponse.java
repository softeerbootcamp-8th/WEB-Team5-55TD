package com.ootd.pickup.bid.dto.response;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.consignments.domain.Certificate;

public record MyBidListItemResponse(
    Long auctionId,
    GetCardDetailResponse card,
    String grade,
    Long myBidPrice,
    Long currentPrice,
    BidStatus status,
    AuctionStatus auctionStatus) {

  public static MyBidListItemResponse of(Bid myLastBid, Certificate certificate, Long currentPrice) {
    Auction auction = myLastBid.getAuction();
    return new MyBidListItemResponse(
        auction.getAuctionId(),
        GetCardDetailResponse.from(auction.getConsignment().getCard()),
        certificate != null ? certificate.getGradeDisplay() : null,
        myLastBid.getBidPrice(),
        currentPrice,
        myLastBid.getBidStatus(),
        auction.getAuctionStatus());
  }
}
