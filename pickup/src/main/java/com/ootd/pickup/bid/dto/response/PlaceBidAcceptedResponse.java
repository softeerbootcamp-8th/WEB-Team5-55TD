package com.ootd.pickup.bid.dto.response;

import java.time.LocalDateTime;

/** 현재가 갱신만 동기로 확정하고 Bid 기록은 비동기로 미룬 결과를 나타낸다. 응답 시점에는 아직 Bid row가 생성되지 않았을 수 있어 bidId를 포함하지 않는다. */
public record PlaceBidAcceptedResponse(
    Long auctionId, Long memberId, Long bidPrice, LocalDateTime acceptedAt) {}
