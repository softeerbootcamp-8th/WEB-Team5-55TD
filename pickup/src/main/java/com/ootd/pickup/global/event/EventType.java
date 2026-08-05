package com.ootd.pickup.global.event;

import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.auction.event.AuctionEndedNotificationEvent;
import com.ootd.pickup.auction.event.AuctionStartedNotificationEvent;

/**
 * 일어난 사건의 종류. {@code outbox_event.event_type}에 이름 그대로 저장되고, Redis 채널 메시지에도 같은 값이 실린다.
 *
 * <p>{@link AggregateType}과 같은 이유로 문자열이 아니라 enum이다. 오타나 표기 차이가 컴파일 단계에서 걸러진다.
 *
 * <p>상수는 <b>사건</b>을 가리키고 계열은 가리키지 않는다. 같은 사건이라도 필요한 보장이 갈리면 클래스가 둘로 나뉜다. 경매 종료는 정산이 정확히 한 번 돌아야 하므로
 * {@link MessageQueueEvent}가 필요하고, 동시에 화면이 즉시 바뀌어야 하므로 {@link NotificationEvent}도 필요하다. 전달 방식이 다른
 * 같은 사건이므로 상수를 공유한다.
 *
 * <p>계열을 고르는 일은 받는 쪽에서 구조적으로 정해진다. SQS 소비자는 Outbox를 거친 것만 받으므로 항상 {@link
 * #messageQueueEventClass()}, Redis 구독자는 채널로 온 것만 받으므로 항상 {@link #notificationEventClass()}를 쓴다.
 * 런타임에 계열을 분간할 필요가 없다.
 *
 * <p>대응 타입을 여기 함께 두는 이유는 어긋날 여지를 없애기 위해서다. 문자열과 매핑 표를 따로 두면 새 사건을 추가할 때 표에 등록하는 것을 잊을 수 있고, 그러면 받는
 * 쪽이 그 이벤트를 되돌리지 못한 채 조용히 쌓인다.
 *
 * <p>저장되는 값이 클래스 이름과 분리돼 있으므로 이벤트 클래스를 옮기거나 이름을 바꿔도 큐에 남아 있던 메시지가 깨지지 않는다. 반대로 <b>상수 이름을 바꾸면</b> 이미
 * 저장된 행과 전송된 메시지를 되돌릴 수 없다.
 *
 * <p>{@code event_type} 컬럼이 {@code VARCHAR(50)}이므로 50자를 넘는 상수는 저장이 실패한다.
 */
public enum EventType {
  /** 예정된 경매가 시작 시각에 도달해 입찰이 열렸다. 반드시 한 번 처리해야 할 후속이 없어 알림 계열만 둔다. */
  AUCTION_STARTED(null, AuctionStartedNotificationEvent.class),
  /** 경매가 종료 시각에 도달해 낙찰/유찰이 확정됐다. 정산(메시지 큐)과 화면 갱신(알림)이 모두 필요하다. */
  AUCTION_ENDED(AuctionEndedMessageQueueEvent.class, AuctionEndedNotificationEvent.class),
  /** 입찰로 현재가가 갱신됐다. 화면 갱신만 필요하다. */
  AUCTION_BID_UPDATED(null, AuctionBidUpdatedNotificationEvent.class);

  private final Class<? extends MessageQueueEvent> messageQueueEventClass;
  private final Class<? extends NotificationEvent> notificationEventClass;

  EventType(
      Class<? extends MessageQueueEvent> messageQueueEventClass,
      Class<? extends NotificationEvent> notificationEventClass) {
    this.messageQueueEventClass = messageQueueEventClass;
    this.notificationEventClass = notificationEventClass;
  }

  /**
   * 이 사건의 메시지 큐 계열 구현 타입. SQS 소비자가 payload를 되돌릴 대상이다.
   *
   * <p>짝이 없는 사건에서 부르면 예외를 던진다. Outbox를 거친 메시지는 메시지 큐 계열일 수밖에 없으므로 없는 짝을 찾는 것은 프로그래밍 오류다. {@code
   * Optional}로 감싸 호출자마다 처리하게 하면 그 오류가 조용히 넘어간다.
   *
   * @return 역직렬화 대상 타입
   * @throws IllegalStateException 이 사건에 메시지 큐 계열이 없는 경우
   */
  public Class<? extends MessageQueueEvent> messageQueueEventClass() {
    if (messageQueueEventClass == null) {
      throw new IllegalStateException("메시지 큐 계열이 없는 사건입니다 - eventType=" + name());
    }
    return messageQueueEventClass;
  }

  /**
   * 이 사건의 알림 계열 구현 타입. Redis 구독자가 payload를 되돌릴 대상이다.
   *
   * @return 역직렬화 대상 타입
   * @throws IllegalStateException 이 사건에 알림 계열이 없는 경우
   */
  public Class<? extends NotificationEvent> notificationEventClass() {
    if (notificationEventClass == null) {
      throw new IllegalStateException("알림 계열이 없는 사건입니다 - eventType=" + name());
    }
    return notificationEventClass;
  }
}
