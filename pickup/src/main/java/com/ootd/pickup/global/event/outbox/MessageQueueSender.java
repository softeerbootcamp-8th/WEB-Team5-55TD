package com.ootd.pickup.global.event.outbox;

import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.MessageQueueEvent;

/**
 * 적재된 이벤트를 실제 메시지 큐로 내보내는 계약.
 *
 * <p>이 계약은 릴레이 전용이다. 도메인은 {@link EventProducer}만 쓴다. 여기로 직접 보내면 Outbox를 우회해 도메인 커밋과 발행이 갈라지고, 커밋 직후
 * 프로세스가 죽었을 때 이벤트가 사라진다. 그 유실을 막는 것이 Outbox 를 두는 이유 전부다.
 *
 * <p>포트를 구현체 쪽이 아니라 이 패키지에 두는 이유는 이 계약을 필요로 하는 쪽이 릴레이이기 때문이다. 릴레이는 "내보낼 수단이 있다"만 알고 그것이 SQS 인지는
 * 모른다. 반대로 두면 릴레이가 전송 기술을 알게 된다.
 *
 * <p>넘어오는 이벤트는 {@link RelayedOutboxEvent}다. payload 가 적재 시점에 이미 직렬화돼 있으므로 구현체는 그 원문을 본문으로 쓰면 된다. 다시
 * 직렬화하면 문자열이 한 번 더 감싸진다.
 */
public interface MessageQueueSender {

  /**
   * 이벤트를 큐로 보낸다.
   *
   * <p>예외를 던지면 릴레이가 그 행을 발행 완료로 표시하지 않고 다음 주기에 다시 시도한다. 전송에 실패했는데 조용히 성공한 것처럼 굴면 그 이벤트가 영구히 사라진다.
   *
   * @param event 발행할 이벤트
   */
  void send(MessageQueueEvent event);
}
