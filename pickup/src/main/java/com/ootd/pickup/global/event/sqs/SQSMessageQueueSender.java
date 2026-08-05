package com.ootd.pickup.global.event.sqs;

import com.ootd.pickup.global.event.MessageQueueEvent;
import com.ootd.pickup.global.event.outbox.MessageQueueSender;
import com.ootd.pickup.global.event.outbox.RelayedOutboxEvent;
import org.springframework.stereotype.Component;

/**
 * Outbox 릴레이가 넘긴 이벤트를 SQS FIFO 큐로 보내는 {@link MessageQueueSender} 구현체.
 *
 * <p>전송 로직은 아직 비어 있다. 이 상태로 릴레이를 켜면 전송이 성공한 것처럼 보여 행이 발행 완료로 표시되고, 유실이 허용되지 않는 이벤트가 조용히 사라진다. 그래서
 * {@code scheduler.outbox.enabled} 기본값을 꺼둔다.
 *
 * <p>구현할 때 필요한 값은 전부 {@link RelayedOutboxEvent}에 있다.
 *
 * <ul>
 *   <li>{@code MessageBody} — {@link RelayedOutboxEvent#payload()}를 <b>그대로</b> 쓴다. 적재 시점에 직렬화된 원문이라
 *       다시 직렬화하면 문자열이 한 번 더 감싸진다.
 *   <li>{@code MessageGroupId} — {@code aggregateType} 과 {@code aggregateId} 를 묶어 만든다. FIFO 큐는 같은
 *       그룹 안에서만 순서를 보장하므로 한 경매의 사건이 모두 같은 그룹에 들어가야 한다.
 *   <li>{@code MessageDeduplicationId} — {@code eventId}. 릴레이가 재시도하면 같은 이벤트가 다시 오므로 큐에서 걸러낸다.
 * </ul>
 */
@Component
public class SQSMessageQueueSender implements MessageQueueSender {

  @Override
  public void send(MessageQueueEvent event) {}
}
