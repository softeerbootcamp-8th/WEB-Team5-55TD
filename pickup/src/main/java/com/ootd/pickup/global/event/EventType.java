package com.ootd.pickup.global.event;

/**
 * 이벤트의 직렬화 타입 키. {@link DomainEvent#eventType()}이 반환하는 값이다.
 *
 * <p>문자열을 자유롭게 반환하게 두면 오타나 임의 수정으로 값이 어긋나도 컴파일 시점에 걸러지지 않는다. enum으로 제약해두면 새 이벤트 타입을 추가하거나 이름을 바꿀 때
 * 이 목록부터 고치게 되고, 그 값을 참조하는 모든 코드(발행 측 구현체, 소비 측 역직렬화 분기)가 컴파일 시점에 함께 갱신된다.
 *
 * <p>이벤트가 늘어나면 여기에 추가한다.
 */
public enum EventType {
  AUCTION_STARTED,
  AUCTION_ENDED,
  AUCTION_BID_UPDATED
}
