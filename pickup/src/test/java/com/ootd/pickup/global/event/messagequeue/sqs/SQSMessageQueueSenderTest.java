package com.ootd.pickup.global.event.messagequeue.sqs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.messagequeue.outbox.BatchSendResult;
import com.ootd.pickup.global.event.messagequeue.outbox.RelayedOutboxEvent;
import com.ootd.pickup.global.event.messagequeue.sqs.config.SQSProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;

class SQSMessageQueueSenderTest {

  private static final String QUEUE_URL =
      "https://sqs.ap-northeast-2.amazonaws.com/123456789012/pickup-event.fifo";

  private SqsClient eventSqsClient;
  private SQSMessageQueueSender sqsMessageQueueSender;

  @BeforeEach
  void 전송할_큐와_클라이언트를_준비한다() {
    eventSqsClient = mock(SqsClient.class);
    SQSProperties properties =
        new SQSProperties(
            QUEUE_URL,
            "ap-northeast-2",
            Duration.ofSeconds(20),
            Duration.ofSeconds(30),
            10,
            4,
            Duration.ofSeconds(15));
    sqsMessageQueueSender = new SQSMessageQueueSender(eventSqsClient, properties);
    // 기본값: 요청에 실린 항목 전부를 성공으로 응답한다. 실패를 검증하는 테스트는 개별적으로 스텁을 덮어쓴다.
    given(eventSqsClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
        .willAnswer(invocation -> allSucceeded(invocation.getArgument(0)));
  }

  @Test
  void 설정된_큐로_전송한다() {
    // given
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{\"auctionId\":1024}");

    // when
    sqsMessageQueueSender.sendBatch(List.of(event));

    // then
    assertThat(capturedRequest().queueUrl()).isEqualTo(QUEUE_URL);
  }

  @Test
  void 적재된_payload_원문을_그대로_본문에_싣는다() {
    // given — 다시 직렬화하면 JSON 이 문자열로 한 번 더 감싸진다
    String payload = "{\"eventId\":\"event-1\",\"auctionId\":1024,\"winningPrice\":50000}";
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, payload);

    // when
    sqsMessageQueueSender.sendBatch(List.of(event));

    // then
    assertThat(onlyEntry().messageBody()).isEqualTo(payload);
  }

  @Test
  void eventType을_메시지_속성에_싣는다() {
    // given — 본문에는 eventType 이 없어 이 속성이 없으면 소비자가 되돌릴 타입을 알 수 없다
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");

    // when
    sqsMessageQueueSender.sendBatch(List.of(event));

    // then
    MessageAttributeValue attribute = onlyEntry().messageAttributes().get("eventType");
    assertThat(attribute.stringValue()).isEqualTo(EventType.AUCTION_ENDED.name());
    assertThat(attribute.dataType()).isEqualTo("String");
  }

  @Test
  void traceParent이_있으면_메시지_속성에_싣는다() {
    // given
    String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}", traceParent);

    // when
    sqsMessageQueueSender.sendBatch(List.of(event));

    // then
    MessageAttributeValue attribute = onlyEntry().messageAttributes().get("traceParent");
    assertThat(attribute.stringValue()).isEqualTo(traceParent);
    assertThat(attribute.dataType()).isEqualTo("String");
  }

  @Test
  void traceParent이_없으면_메시지_속성에_싣지_않는다() {
    // given — 적재 당시 활성 스팬이 없었던 경우
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}", null);

    // when
    sqsMessageQueueSender.sendBatch(List.of(event));

    // then
    assertThat(onlyEntry().messageAttributes()).doesNotContainKey("traceParent");
  }

  @Test
  void 애그리거트_종류와_식별자를_묶어_메시지_그룹을_만든다() {
    // given
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");

    // when
    sqsMessageQueueSender.sendBatch(List.of(event));

    // then
    assertThat(onlyEntry().messageGroupId()).isEqualTo("AUCTION:1024");
  }

  @Test
  void 같은_애그리거트의_이벤트를_한_배치로_보내면_같은_메시지_그룹으로_간다() {
    // given — 그룹이 갈라지면 한 경매의 시작·종료 순서 보장이 사라진다
    RelayedOutboxEvent first = relayedEvent("event-1", 1024L, "{}");
    RelayedOutboxEvent second = relayedEvent("event-2", 1024L, "{}");

    // when
    sqsMessageQueueSender.sendBatch(List.of(first, second));

    // then
    assertThat(capturedRequest().entries())
        .extracting(SendMessageBatchRequestEntry::messageGroupId)
        .containsExactly("AUCTION:1024", "AUCTION:1024");
  }

  @Test
  void eventId를_배치_항목_식별자이자_중복_제거_식별자로_쓴다() {
    // given — 릴레이가 재시도하면 같은 이벤트가 다시 오므로 큐가 걸러낸다
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");

    // when
    sqsMessageQueueSender.sendBatch(List.of(event));

    // then
    assertThat(onlyEntry().id()).isEqualTo("event-1");
    assertThat(onlyEntry().messageDeduplicationId()).isEqualTo("event-1");
  }

  @Test
  void 호출_자체가_실패하면_예외를_그대로_던진다() {
    // given — 삼키면 릴레이가 이 청크를 발행 완료로 표시해 이벤트가 영구히 사라진다
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");
    willThrow(SdkClientException.create("큐 장애"))
        .given(eventSqsClient)
        .sendMessageBatch(any(SendMessageBatchRequest.class));

    // when & then
    assertThatThrownBy(() -> sqsMessageQueueSender.sendBatch(List.of(event)))
        .isInstanceOf(SdkClientException.class);
  }

  @Test
  void 일부만_실패하면_성공_실패_목록에_그대로_나뉘어_담긴다() {
    // given — SendMessageBatch는 부분 실패를 예외가 아니라 정상 응답으로 돌려준다
    RelayedOutboxEvent succeeding = relayedEvent("event-1", 1024L, "{}");
    RelayedOutboxEvent failing = relayedEvent("event-2", 2048L, "{}");
    given(eventSqsClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
        .willReturn(
            SendMessageBatchResponse.builder()
                .successful(
                    SendMessageBatchResultEntry.builder()
                        .id("event-1")
                        .messageId("sqs-message-id")
                        .build())
                .failed(
                    BatchResultErrorEntry.builder()
                        .id("event-2")
                        .code("InvalidParameterValue")
                        .senderFault(true)
                        .message("잘못된 요청")
                        .build())
                .build());

    // when
    BatchSendResult result = sqsMessageQueueSender.sendBatch(List.of(succeeding, failing));

    // then
    assertThat(result.succeededEventIds()).containsExactly("event-1");
    assertThat(result.failedEvents())
        .extracting(BatchSendResult.FailedEvent::eventId)
        .containsExactly("event-2");
    assertThat(result.failedEvents().get(0).reason()).contains("InvalidParameterValue");
  }

  private static SendMessageBatchResponse allSucceeded(SendMessageBatchRequest request) {
    List<SendMessageBatchResultEntry> successful =
        request.entries().stream()
            .map(
                entry ->
                    SendMessageBatchResultEntry.builder()
                        .id(entry.id())
                        .messageId("sqs-message-id-" + entry.id())
                        .build())
            .toList();
    return SendMessageBatchResponse.builder().successful(successful).build();
  }

  private SendMessageBatchRequest capturedRequest() {
    ArgumentCaptor<SendMessageBatchRequest> captor =
        ArgumentCaptor.forClass(SendMessageBatchRequest.class);
    then(eventSqsClient).should().sendMessageBatch(captor.capture());
    return captor.getValue();
  }

  private SendMessageBatchRequestEntry onlyEntry() {
    List<SendMessageBatchRequestEntry> entries = capturedRequest().entries();
    assertThat(entries).hasSize(1);
    return entries.get(0);
  }

  private RelayedOutboxEvent relayedEvent(String eventId, Long auctionId, String payload) {
    return relayedEvent(eventId, auctionId, payload, null);
  }

  private RelayedOutboxEvent relayedEvent(
      String eventId, Long auctionId, String payload, String traceParent) {
    return new RelayedOutboxEvent(
        eventId,
        AggregateType.AUCTION,
        auctionId,
        EventType.AUCTION_ENDED,
        LocalDateTime.of(2026, 8, 5, 10, 0),
        payload,
        traceParent);
  }
}
