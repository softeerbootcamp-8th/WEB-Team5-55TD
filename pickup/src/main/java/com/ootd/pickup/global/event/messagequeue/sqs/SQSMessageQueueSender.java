package com.ootd.pickup.global.event.messagequeue.sqs;

import com.ootd.pickup.global.event.messagequeue.outbox.BatchSendResult;
import com.ootd.pickup.global.event.messagequeue.outbox.BatchSendResult.FailedEvent;
import com.ootd.pickup.global.event.messagequeue.outbox.MessageQueueSender;
import com.ootd.pickup.global.event.messagequeue.outbox.OutboxEventScheduler;
import com.ootd.pickup.global.event.messagequeue.outbox.RelayedOutboxEvent;
import com.ootd.pickup.global.event.messagequeue.sqs.config.SQSProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;

/**
 * Outbox 릴레이가 넘긴 이벤트를 SQS FIFO 큐로 보내는 {@link MessageQueueSender} 구현체.
 *
 * <p>채우는 값은 전부 {@link RelayedOutboxEvent}에서 나온다.
 *
 * <ul>
 *   <li>{@code MessageBody} — {@link RelayedOutboxEvent#payload()}를 <b>그대로</b> 쓴다. 이미 직렬화된 원문이라 다시
 *       직렬화하면 문자열이 한 번 더 감싸진다.
 *   <li>{@code MessageAttributes} — {@code eventType}, 그리고 있으면 {@code traceParent}. 소비자가 본문을 되돌릴
 *       타입과 이어 붙일 트레이스를 여기서 얻는다.
 *   <li>{@code MessageGroupId} — {@link RelayedOutboxEvent#messageGroupId()}
 *   <li>{@code MessageDeduplicationId} — {@code eventId}. 릴레이가 재시도하면 같은 이벤트가 다시 오므로 큐가 걸러낸다.
 * </ul>
 *
 * <p>소비자에게 전달되는 것은 앞의 둘뿐이다. 뒤의 둘은 큐가 쓴다.
 *
 * <p>{@code eventType} 을 속성에 따로 실어야 하는 이유는 <b>본문에 그 값이 없기 때문</b>이다. 이벤트 record 는 {@code
 * eventType()} 을 컴포넌트가 아니라 오버라이드 메서드로 두어 직렬화 대상에서 빠진다. 이 속성을 빠뜨리면 {@link SQSEventConsumer}가 어떤 타입으로
 * 되돌릴지 알 수 없다. {@code traceParent}도 같은 이유로 속성에 싣는다 — 본문을 건드리면 적재 시점의 원문 JSON이 아니게 된다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event.sqs.enabled", havingValue = "true")
public class SQSMessageQueueSender implements MessageQueueSender {

  /** 소비자가 본문을 되돌릴 타입을 고르는 근거. 이름을 바꾸면 이미 큐에 있는 메시지를 되돌릴 수 없다. */
  private static final String EVENT_TYPE_ATTRIBUTE = "eventType";

  /** 적재 시점 트레이스를 잇는 W3C traceparent. 적재 당시 활성 스팬이 없었으면 이 속성 자체가 없다. */
  private static final String TRACE_PARENT_ATTRIBUTE = "traceParent";

  private static final String STRING_DATA_TYPE = "String";

  private final SqsClient eventSqsClient;
  private final SQSProperties sqsProperties;

  /**
   * 이벤트를 최대 10건(SQS {@code SendMessageBatch} 하드 리밋)씩 묶어 보낸다.
   *
   * <p>호출 자체의 실패(네트워크 등)는 감싸지 않고 그대로 던진다. {@link OutboxEventScheduler}가 이 예외를 받아 청크 전체를 이번 주기에서 실패로
   * 취급하므로, 여기서 잡거나 로그를 남기면 같은 실패가 매 주기 중복해서 쏟아진다. 건별 부분 실패는 예외가 아니라 응답의 {@code failed} 목록으로 온다 —
   * {@link BatchSendResult}로 그대로 옮긴다.
   *
   * @param events 보낼 이벤트. 최대 10건
   * @return 건별 성공/실패 결과
   * @throws software.amazon.awssdk.core.exception.SdkException 호출 자체가 실패한 경우
   */
  @Override
  public BatchSendResult sendBatch(List<RelayedOutboxEvent> events) {
    List<SendMessageBatchRequestEntry> entries = events.stream().map(this::toEntry).toList();

    SendMessageBatchResponse response =
        eventSqsClient.sendMessageBatch(
            SendMessageBatchRequest.builder()
                .queueUrl(sqsProperties.queueUrl())
                .entries(entries)
                .build());

    List<String> succeeded =
        response.hasSuccessful()
            ? response.successful().stream().map(SendMessageBatchResultEntry::id).toList()
            : List.of();
    List<FailedEvent> failed =
        response.hasFailed()
            ? response.failed().stream()
                .map(entry -> new FailedEvent(entry.id(), describeFailure(entry)))
                .toList()
            : List.of();
    return new BatchSendResult(succeeded, failed);
  }

  private SendMessageBatchRequestEntry toEntry(RelayedOutboxEvent event) {
    return SendMessageBatchRequestEntry.builder()
        .id(event.eventId())
        .messageBody(event.payload())
        .messageAttributes(buildMessageAttributes(event))
        .messageGroupId(event.messageGroupId())
        .messageDeduplicationId(event.eventId())
        .build();
  }

  private static String describeFailure(BatchResultErrorEntry failed) {
    return "%s(senderFault=%s, message=%s)"
        .formatted(failed.code(), failed.senderFault(), failed.message());
  }

  /** {@code traceParent}는 없을 수 있으므로(적재 당시 활성 스팬이 없었던 경우) 있을 때만 속성에 싣는다. */
  private Map<String, MessageAttributeValue> buildMessageAttributes(RelayedOutboxEvent event) {
    Map<String, MessageAttributeValue> attributes = new HashMap<>();
    attributes.put(
        EVENT_TYPE_ATTRIBUTE,
        MessageAttributeValue.builder()
            .dataType(STRING_DATA_TYPE)
            .stringValue(event.eventType().name())
            .build());
    if (event.traceParent() != null) {
      attributes.put(
          TRACE_PARENT_ATTRIBUTE,
          MessageAttributeValue.builder()
              .dataType(STRING_DATA_TYPE)
              .stringValue(event.traceParent())
              .build());
    }
    return attributes;
  }
}
