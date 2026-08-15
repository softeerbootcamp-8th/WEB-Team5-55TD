package com.ootd.pickup.global.event.messagequeue.sqs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.messagequeue.outbox.RelayedOutboxEvent;
import com.ootd.pickup.global.event.messagequeue.sqs.config.SQSProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

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
            QUEUE_URL, "ap-northeast-2", Duration.ofSeconds(20), Duration.ofSeconds(30), 10);
    sqsMessageQueueSender = new SQSMessageQueueSender(eventSqsClient, properties);
  }

  @Test
  void 설정된_큐로_전송한다() {
    // given
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{\"auctionId\":1024}");

    // when
    sqsMessageQueueSender.send(event);

    // then
    assertThat(capturedRequest().queueUrl()).isEqualTo(QUEUE_URL);
  }

  @Test
  void 적재된_payload_원문을_그대로_본문에_싣는다() {
    // given — 다시 직렬화하면 JSON 이 문자열로 한 번 더 감싸진다
    String payload = "{\"eventId\":\"event-1\",\"auctionId\":1024,\"winningPrice\":50000}";
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, payload);

    // when
    sqsMessageQueueSender.send(event);

    // then
    assertThat(capturedRequest().messageBody()).isEqualTo(payload);
  }

  @Test
  void eventType을_메시지_속성에_싣는다() {
    // given — 본문에는 eventType 이 없어 이 속성이 없으면 소비자가 되돌릴 타입을 알 수 없다
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");

    // when
    sqsMessageQueueSender.send(event);

    // then
    MessageAttributeValue attribute = capturedRequest().messageAttributes().get("eventType");
    assertThat(attribute.stringValue()).isEqualTo(EventType.AUCTION_ENDED.name());
    assertThat(attribute.dataType()).isEqualTo("String");
  }

  @Test
  void traceParent이_있으면_메시지_속성에_싣는다() {
    // given
    String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}", traceParent);

    // when
    sqsMessageQueueSender.send(event);

    // then
    MessageAttributeValue attribute = capturedRequest().messageAttributes().get("traceParent");
    assertThat(attribute.stringValue()).isEqualTo(traceParent);
    assertThat(attribute.dataType()).isEqualTo("String");
  }

  @Test
  void traceParent이_없으면_메시지_속성에_싣지_않는다() {
    // given — 적재 당시 활성 스팬이 없었던 경우
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}", null);

    // when
    sqsMessageQueueSender.send(event);

    // then
    assertThat(capturedRequest().messageAttributes()).doesNotContainKey("traceParent");
  }

  @Test
  void 애그리거트_종류와_식별자를_묶어_메시지_그룹을_만든다() {
    // given
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");

    // when
    sqsMessageQueueSender.send(event);

    // then
    assertThat(capturedRequest().messageGroupId()).isEqualTo("AUCTION:1024");
  }

  @Test
  void 같은_애그리거트의_이벤트는_같은_메시지_그룹으로_간다() {
    // given — 그룹이 갈라지면 한 경매의 시작·종료 순서 보장이 사라진다
    RelayedOutboxEvent first = relayedEvent("event-1", 1024L, "{}");
    RelayedOutboxEvent second = relayedEvent("event-2", 1024L, "{}");

    // when
    sqsMessageQueueSender.send(first);
    sqsMessageQueueSender.send(second);

    // then
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    then(eventSqsClient).should(times(2)).sendMessage(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(SendMessageRequest::messageGroupId)
        .containsExactly("AUCTION:1024", "AUCTION:1024");
  }

  @Test
  void eventId를_중복_제거_식별자로_쓴다() {
    // given — 릴레이가 재시도하면 같은 이벤트가 다시 오므로 큐가 걸러낸다
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");

    // when
    sqsMessageQueueSender.send(event);

    // then
    assertThat(capturedRequest().messageDeduplicationId()).isEqualTo("event-1");
  }

  @Test
  void 전송에_실패하면_예외를_그대로_던진다() {
    // given — 삼키면 릴레이가 발행 완료로 표시해 이벤트가 영구히 사라진다
    RelayedOutboxEvent event = relayedEvent("event-1", 1024L, "{}");
    willThrow(SdkClientException.create("큐 장애"))
        .given(eventSqsClient)
        .sendMessage(any(SendMessageRequest.class));

    // when & then
    assertThatThrownBy(() -> sqsMessageQueueSender.send(event))
        .isInstanceOf(SdkClientException.class);
  }

  private SendMessageRequest capturedRequest() {
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    then(eventSqsClient).should().sendMessage(captor.capture());
    return captor.getValue();
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
