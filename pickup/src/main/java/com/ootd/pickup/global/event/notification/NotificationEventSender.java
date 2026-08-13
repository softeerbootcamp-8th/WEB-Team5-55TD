package com.ootd.pickup.global.event.notification;

import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;

/**
 * 알림 이벤트를 전송 계층(Redis Pub/Sub 등)으로 내보내는 계약.
 *
 * <p>도메인은 이 인터페이스를 쓰지 않는다. 도메인이 부르는 진입점은 {@link EventPublisher}이고, 커밋 이후 이 계약을 호출하는 것은 {@link
 * NotificationEventListener}다.
 *
 * <p>구현체는 커밋 여부를 확인하지 않는다. 호출 시점이 이미 커밋 이후라는 전제는 {@link EventPublisher}가 지킨다.
 */
public interface NotificationEventSender {

  /**
   * 알림 이벤트를 전송한다.
   *
   * @param event 전송할 알림 이벤트
   * @throws RuntimeException 전송 계층이 실패한 경우. 호출자가 잡아 삼킨다
   */
  void send(NotificationEvent event);
}
