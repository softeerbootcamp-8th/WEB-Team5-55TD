package com.ootd.pickup.auction.dto.request;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionSchedulePolicy;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record CreateAuctionRequest(
    @NotNull Long consignmentId,
    @NotNull @Min(value = MINIMUM_STARTING_PRICE, message = "희망 시작가는 1,000원 이상이어야 합니다.")
        Long startingPrice,
    @NotNull @Positive Long reserve,
    @NotNull @Future LocalDateTime scheduledStartAt,
    @NotBlank String title,
    String description) {

  /** 최소 입찰 단위(시작가의 5%)가 원 단위로 의미를 갖도록 시작가에 하한을 둔다. */
  public static final long MINIMUM_STARTING_PRICE = 1_000L;

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
