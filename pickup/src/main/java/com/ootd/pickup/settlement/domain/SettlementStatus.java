package com.ootd.pickup.settlement.domain;

// 정산 컨슈머는 금액을 계산한 뒤 완결된 한 줄로 INSERT하므로 현재는 COMPLETED만 존재한다.
// 환불/취소 등 후속 상태가 필요해지면 이 enum에 값을 추가한다.
public enum SettlementStatus {
  COMPLETED
}
