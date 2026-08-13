package com.ootd.pickup.auction.dto.request;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionSchedulePolicy;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateAuctionRequest(
    @NotNull Long consignmentId,
    @NotNull @Positive Long startingPrice,
    @NotNull @Positive Long reserve,
    @NotNull @Future LocalDateTime scheduledStartAt,
    @NotBlank @Size(max = 100, message = "경매 제목은 100자 이하여야 합니다.") String title,
    @Size(max = 1000, message = "경매 설명은 1000자 이하여야 합니다.") String description) {

  public Auction toEntity(Consignment consignment, Long bidIncrement) {
    return Auction.builder()
        .consignment(consignment)
        .startedAt(scheduledStartAt)
        .endedAt(AuctionSchedulePolicy.initialEndAt(scheduledStartAt))
        .auctionStatus(AuctionStatus.SCHEDULED)
        .startingPrice(startingPrice)
        .reservePrice(reserve)
        .bidIncrement(bidIncrement)
        .title(title)
        .description(description)
        .build();
  }
}
