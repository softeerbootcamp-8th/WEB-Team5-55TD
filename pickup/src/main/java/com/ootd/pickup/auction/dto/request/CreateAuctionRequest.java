package com.ootd.pickup.auction.dto.request;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionSchedulePolicy;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record CreateAuctionRequest(
    @NotNull Long consignmentId,
    @NotNull @Positive Long startingPrice,
    @NotNull @Positive Long reserve,
    @NotNull @Future LocalDateTime scheduledStartAt) {

  public Auction toEntity(Consignment consignment, Long bidIncrement) {
    LocalDateTime confirmedStartAt = AuctionSchedulePolicy.confirmStartAt(scheduledStartAt);
    return Auction.builder()
        .consignment(consignment)
        .startedAt(confirmedStartAt)
        .endedAt(AuctionSchedulePolicy.initialEndAt(confirmedStartAt))
        .auctionStatus(AuctionStatus.SCHEDULED)
        .startingPrice(startingPrice)
        .reservePrice(reserve)
        .bidIncrement(bidIncrement)
        .build();
  }
}
