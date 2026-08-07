package com.ootd.pickup.settlement.domain;

/** 경매 마감 후 발생하는 정산 원장의 종류. */
public enum SettlementType {
  // 낙찰자 결제
  WINNER_PAYMENT,
  // 판매자 정산
  SELLER_PAYOUT,
}
