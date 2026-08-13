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
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateAuctionRequest(
    @NotNull Long consignmentId,
    @NotNull @Min(value = MINIMUM_STARTING_PRICE, message = "희망 시작가는 1,000원 이상이어야 합니다.")
        Long startingPrice,
    @NotNull @Positive Long reserve,
    @NotNull @Future LocalDateTime scheduledStartAt,
    @NotBlank @Size(max = 100, message = "경매 제목은 100자 이하여야 합니다.") String title,
    @Size(max = 1000, message = "경매 설명은 1000자 이하여야 합니다.") String description) {

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
