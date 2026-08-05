package com.ootd.pickup.global.event.sqs;

import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import com.ootd.pickup.global.event.outbox.MessageQueueSender;
import com.ootd.pickup.global.event.outbox.RelayedOutboxEvent;
import org.springframework.stereotype.Component;

/**
 * Outbox 릴레이가 넘긴 이벤트를 SQS FIFO 큐로 보내는 {@link MessageQueueSender} 구현체.
 *
 * <p><b>전송 로직이 아직 비어 있다.</b> 이 상태로 릴레이를 켜면 전송이 성공한 것처럼 보여 유실이 허용되지 않는 이벤트가 조용히 사라진다. 그래서 {@code
 * scheduler.outbox.enabled} 기본값이 꺼져 있다.
 *
 * <p>채울 값은 전부 {@link RelayedOutboxEvent}에 있다.
 *
 * <ul>
 *   <li>{@code MessageBody} — {@link RelayedOutboxEvent#payload()}를 <b>그대로</b> 쓴다. 이미 직렬화된 원문이라 다시
 *       직렬화하면 문자열이 한 번 더 감싸진다.
 *   <li>{@code MessageAttributes} — {@code eventType} 하나. 소비자가 본문을 되돌릴 타입을 고르는 근거다.
 *   <li>{@code MessageGroupId} — {@code aggregateType} 과 {@code aggregateId} 를 묶어 만든다. FIFO 큐는 같은
 *       그룹 안에서만 순서를 보장하므로 한 경매의 사건이 모두 같은 그룹에 들어가야 한다.
 *   <li>{@code MessageDeduplicationId} — {@code eventId}. 릴레이가 재시도하면 같은 이벤트가 다시 오므로 큐가 걸러낸다.
 * </ul>
 *
 * <p>소비자에게 전달되는 것은 앞의 둘뿐이다. 뒤의 둘은 큐가 쓴다.
 *
 * <p>{@code eventType} 을 속성에 따로 실어야 하는 이유는 <b>본문에 그 값이 없기 때문</b>이다. 이벤트 record 는 {@code
 * eventType()} 을 컴포넌트가 아니라 오버라이드 메서드로 두어 직렬화 대상에서 빠진다. 이 속성을 빠뜨리면 소비자는 어떤 타입으로 되돌릴지 알 수 없다.
 *
 * <p>소비자는 이 속성을 {@link EventType} 으로 바꿔 {@link EventType#messageQueueEventClass()} 가 준 타입에 본문을
 * 역직렬화하고, {@code eventId} 로 중복을 걸러낸다.
 */
@Component
public class SQSMessageQueueSender implements MessageQueueSender {

  @Override
  public void send(MessageQueueEvent event) {
    // TODO: SQS FIFO 큐로 전송한다. 채울 값은 위 목록 참고
  }
}
