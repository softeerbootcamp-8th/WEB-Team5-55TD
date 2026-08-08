package com.ootd.pickup.global.event.sqs;

import com.ootd.pickup.global.event.outbox.MessageQueueSender;
import com.ootd.pickup.global.event.outbox.OutboxEventScheduler;
import com.ootd.pickup.global.event.outbox.RelayedOutboxEvent;
import com.ootd.pickup.global.event.sqs.config.SQSProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Outbox 릴레이가 넘긴 이벤트를 SQS FIFO 큐로 보내는 {@link MessageQueueSender} 구현체.
 *
 * <p>채우는 값은 전부 {@link RelayedOutboxEvent}에서 나온다.
 *
 * <ul>
 *   <li>{@code MessageBody} — {@link RelayedOutboxEvent#payload()}를 <b>그대로</b> 쓴다. 이미 직렬화된 원문이라 다시
 *       직렬화하면 문자열이 한 번 더 감싸진다.
 *   <li>{@code MessageAttributes} — {@code eventType} 하나. 소비자가 본문을 되돌릴 타입을 고르는 근거다.
 *   <li>{@code MessageGroupId} — {@link RelayedOutboxEvent#messageGroupId()}
 *   <li>{@code MessageDeduplicationId} — {@code eventId}. 릴레이가 재시도하면 같은 이벤트가 다시 오므로 큐가 걸러낸다.
 * </ul>
 *
 * <p>소비자에게 전달되는 것은 앞의 둘뿐이다. 뒤의 둘은 큐가 쓴다.
 *
 * <p>{@code eventType} 을 속성에 따로 실어야 하는 이유는 <b>본문에 그 값이 없기 때문</b>이다. 이벤트 record 는 {@code
 * eventType()} 을 컴포넌트가 아니라 오버라이드 메서드로 두어 직렬화 대상에서 빠진다. 이 속성을 빠뜨리면 {@link SQSEventConsumer}가 어떤 타입으로
 * 되돌릴지 알 수 없다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event.sqs.enabled", havingValue = "true")
public class SQSMessageQueueSender implements MessageQueueSender {

  /** 소비자가 본문을 되돌릴 타입을 고르는 근거. 이름을 바꾸면 이미 큐에 있는 메시지를 되돌릴 수 없다. */
  private static final String EVENT_TYPE_ATTRIBUTE = "eventType";

  private static final String STRING_DATA_TYPE = "String";

  private final SqsClient eventSqsClient;
  private final SQSProperties sqsProperties;

  /**
   * 이벤트를 큐로 보낸다.
   *
   * <p>실패를 감싸지 않고 그대로 던진다. {@link OutboxEventScheduler}가 이 예외를 받아 해당 애그리거트를 이번 주기에서 차단하고 한 줄로 모아
   * 남기므로, 여기서 잡거나 로그를 남기면 같은 실패가 매 주기 중복해서 쏟아진다.
   *
   * @param event 발행할 이벤트
   * @throws software.amazon.awssdk.core.exception.SdkException 큐 전송에 실패한 경우
   */
  @Override
  public void send(RelayedOutboxEvent event) {
    eventSqsClient.sendMessage(
        SendMessageRequest.builder()
            .queueUrl(sqsProperties.queueUrl())
            .messageBody(event.payload())
            .messageAttributes(
                Map.of(
                    EVENT_TYPE_ATTRIBUTE,
                    MessageAttributeValue.builder()
                        .dataType(STRING_DATA_TYPE)
                        .stringValue(event.eventType().name())
                        .build()))
            .messageGroupId(event.messageGroupId())
            .messageDeduplicationId(event.eventId())
            .build());
  }
}
