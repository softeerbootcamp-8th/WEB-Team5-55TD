package com.ootd.pickup.auction.domain;

public enum AuctionStatus {
    // 경매 시작 대기 중
    SCHEDULED,
    // 경매 진행 중
    ONGOING,
    // 낙찰되어 종료
    WON,
    // 유찰되어 종료
    PASSED
}
