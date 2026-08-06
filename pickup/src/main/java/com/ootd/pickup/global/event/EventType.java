package com.ootd.pickup.global.event;

import com.ootd.pickup.auction.event.AuctionBidUpdatedEvent;

public enum EventType {
  AUCTION_BID_UPDATED(null, AuctionBidUpdatedEvent.class);

  private final Class<? extends MessageQueueEvent> messageQueueEventClass;
  private final Class<? extends NotificationEvent> notificationEventClass;

  EventType(
      Class<? extends MessageQueueEvent> messageQueueEventClass,
      Class<? extends NotificationEvent> notificationEventClass) {
    this.messageQueueEventClass = messageQueueEventClass;
    this.notificationEventClass = notificationEventClass;
  }

  public Class<? extends MessageQueueEvent> messageQueueEventClass() {
    if (messageQueueEventClass == null) {
      throw new IllegalStateException("메시지 큐 계열이 없는 사건입니다 - eventType=" + name());
    }
    return messageQueueEventClass;
  }

  public Class<? extends NotificationEvent> notificationEventClass() {
    if (notificationEventClass == null) {
      throw new IllegalStateException("알림 계열이 없는 사건입니다 - eventType=" + name());
    }
    return notificationEventClass;
  }
}
