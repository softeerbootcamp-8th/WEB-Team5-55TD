package com.ootd.pickup.global.event.outbox;

import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.MessageQueueEvent;

/**
 * 적재된 이벤트를 실제 메시지 큐로 내보내는 계약. 릴레이 전용이다.
 *
 * <p>도메인은 {@link EventProducer}만 쓴다. 여기로 직접 보내면 Outbox를 우회해 도메인 커밋과 발행이 갈라지고, 커밋 직후 프로세스가 죽었을 때
 * 이벤트가 사라진다. 그 유실을 막는 것이 Outbox를 두는 이유 전부다.
 *
 * <p>넘어오는 이벤트는 {@link RelayedOutboxEvent}다. 구현체가 채울 값은 {@code SQSMessageQueueSender}에 정리돼 있다.
 */
public interface MessageQueueSender {

  /**
   * 이벤트를 큐로 보낸다.
   *
   * <p>예외를 던지면 릴레이가 그 행을 발행 완료로 표시하지 않고 다음 주기에 다시 시도한다. 실패했는데 조용히 성공한 것처럼 굴면 그 이벤트가 영구히 사라진다.
   *
   * @param event 발행할 이벤트
   */
  void send(MessageQueueEvent event);
}
