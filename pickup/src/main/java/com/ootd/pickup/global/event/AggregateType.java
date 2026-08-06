package com.ootd.pickup.global.event;

/**
 * 이벤트가 속한 애그리거트 종류. {@link DomainEvent#aggregateId()}와 짝을 이뤄 사건의 대상을 가리킨다.
 *
 * <p>문자열이 아니라 enum으로 두는 이유는 {@code "Auction"}과 {@code "auction"}의 차이가 컴파일 단계에서 걸러지지 않기 때문이다. 이 값이
 * SQS FIFO 큐의 {@code MessageGroupId}를 구성하므로, 표기가 어긋나면 같은 경매의 이벤트가 서로 다른 순서 그룹으로 갈라지고 순서 보장이 조용히
 * 깨진다.
 *
 * <p>이벤트를 발행하는 애그리거트가 늘어나면 여기에 추가한다.
 */
public enum AggregateType {
  AUCTION
}
