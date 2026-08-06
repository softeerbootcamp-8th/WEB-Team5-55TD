package com.ootd.pickup.global.event.sqs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.global.event.DomainEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.sqs.config.SQSProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SQSEventConsumerTest {

  private static final String QUEUE_URL =
      "https://sqs.ap-northeast-2.amazonaws.com/123456789012/pickup-event.fifo";

  private SqsClient eventSqsClient;
  private ObjectMapper objectMapper;
  private RecordingHandler auctionEndedHandler;

  /** 받은 이벤트를 모아두고, 지정한 이벤트에서만 실패하는 핸들러. */
  private static final class RecordingHandler
      implements EventHandler<AuctionEndedMessageQueueEvent> {

    private final List<AuctionEndedMessageQueueEvent> received = new ArrayList<>();
    private String failingEventId;

    @Override
    public Class<AuctionEndedMessageQueueEvent> eventClass() {
      return AuctionEndedMessageQueueEvent.class;
    }

    @Override
    public void handle(AuctionEndedMessageQueueEvent event) {
      if (event.eventId().equals(failingEventId)) {
        throw new IllegalStateException("핸들러 장애");
      }
      received.add(event);
    }
  }

  @BeforeEach
  void 소비할_큐와_핸들러를_준비한다() {
    eventSqsClient = mock(SqsClient.class);
    objectMapper = JsonMapper.builder().findAndAddModules().build();
    auctionEndedHandler = new RecordingHandler();
    given(eventSqsClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
        .willReturn(DeleteMessageBatchResponse.builder().build());
  }

  @SafeVarargs
  private SQSEventConsumer consumerWith(EventHandler<? extends DomainEvent>... handlers) {
    SQSProperties properties =
        new SQSProperties(
            QUEUE_URL, "ap-northeast-2", Duration.ofSeconds(20), Duration.ofSeconds(30), 10);
    return new SQSEventConsumer(eventSqsClient, properties, objectMapper, List.of(handlers));
  }

  @Test
  void 설정한_폴링_값으로_큐에서_메시지를_받아온다() {
    // given
    givenMessages();

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    ArgumentCaptor<ReceiveMessageRequest> captor =
        ArgumentCaptor.forClass(ReceiveMessageRequest.class);
    then(eventSqsClient).should().receiveMessage(captor.capture());
    ReceiveMessageRequest request = captor.getValue();
    assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
    assertThat(request.maxNumberOfMessages()).isEqualTo(10);
    assertThat(request.waitTimeSeconds()).isEqualTo(20);
    assertThat(request.visibilityTimeout()).isEqualTo(30);
  }

  @Test
  void 되돌릴_타입과_그룹을_알_수_있도록_속성을_함께_요청한다() {
    // given — 요청하지 않으면 응답에 실리지 않는다
    givenMessages();

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    ArgumentCaptor<ReceiveMessageRequest> captor =
        ArgumentCaptor.forClass(ReceiveMessageRequest.class);
    then(eventSqsClient).should().receiveMessage(captor.capture());
    assertThat(captor.getValue().messageAttributeNames()).contains("eventType");
    assertThat(captor.getValue().messageSystemAttributeNames())
        .contains(MessageSystemAttributeName.MESSAGE_GROUP_ID);
  }

  @Test
  void eventType_속성이_가리키는_타입으로_본문을_되돌린다() {
    // given
    AuctionEndedMessageQueueEvent event = auctionEndedEvent("event-1", 1024L);
    givenMessages(message("message-1", "AUCTION:1024", event));

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    assertThat(auctionEndedHandler.received).hasSize(1);
    assertThat(auctionEndedHandler.received.getFirst()).isEqualTo(event);
  }

  @Test
  void 타입이_맞는_핸들러_전부에게_넘긴다() {
    // given
    RecordingHandler another = new RecordingHandler();
    givenMessages(message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)));

    // when
    consumerWith(auctionEndedHandler, another).consumeOnce();

    // then
    assertThat(auctionEndedHandler.received).hasSize(1);
    assertThat(another.received).hasSize(1);
  }

  @Test
  void 처리에_성공한_메시지를_큐에서_지운다() {
    // given
    givenMessages(message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)));

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    assertThat(deletedMessageIds()).containsExactly("message-1");
  }

  @Test
  void 핸들러가_실패한_메시지는_지우지_않는다() {
    // given — 지우면 처리되지 않은 이벤트가 사라진다
    auctionEndedHandler.failingEventId = "event-1";
    givenMessages(message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)));

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  @Test
  void 처리할_핸들러가_없는_메시지는_지우지_않는다() {
    // given — 조용히 버리면 유실이 허용되지 않는 이벤트가 사라진다. 재전달을 거쳐 DLQ 로 보낸다
    givenMessages(message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)));

    // when
    consumerWith().consumeOnce();

    // then
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  @Test
  void 되돌릴_수_없는_메시지는_지우지_않는다() {
    // given — 본문이 깨져 있으면 재시도해도 낫지 않으므로 DLQ 로 보내야 한다
    Message broken =
        Message.builder()
            .messageId("message-1")
            .receiptHandle("receipt-1")
            .body("깨진 본문")
            .messageAttributes(eventTypeAttribute())
            .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, "AUCTION:1"))
            .build();
    givenMessages(broken);

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  @Test
  void 같은_그룹의_앞선_메시지가_실패하면_뒤_메시지를_처리하지_않는다() {
    // given — 계속 처리하면 앞 이벤트가 재전달돼 다시 처리될 때 순서가 역전된다
    auctionEndedHandler.failingEventId = "event-1";
    givenMessages(
        message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)),
        message("message-2", "AUCTION:1024", auctionEndedEvent("event-2", 1024L)));

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    assertThat(auctionEndedHandler.received).isEmpty();
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  @Test
  void 다른_그룹의_실패는_영향을_주지_않는다() {
    // given — 경매가 다르면 FIFO 그룹도 달라 순서를 함께 지킬 필요가 없다
    auctionEndedHandler.failingEventId = "event-1";
    givenMessages(
        message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)),
        message("message-2", "AUCTION:2048", auctionEndedEvent("event-2", 2048L)));

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    assertThat(auctionEndedHandler.received).extracting("eventId").containsExactly("event-2");
    assertThat(deletedMessageIds()).containsExactly("message-2");
  }

  @Test
  void 받은_메시지가_없으면_삭제를_요청하지_않는다() {
    // given
    givenMessages();

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  private void givenMessages(Message... messages) {
    given(eventSqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .willReturn(ReceiveMessageResponse.builder().messages(messages).build());
  }

  private List<String> deletedMessageIds() {
    ArgumentCaptor<DeleteMessageBatchRequest> captor =
        ArgumentCaptor.forClass(DeleteMessageBatchRequest.class);
    then(eventSqsClient).should().deleteMessageBatch(captor.capture());
    return captor.getValue().entries().stream().map(DeleteMessageBatchRequestEntry::id).toList();
  }

  private Message message(String messageId, String messageGroupId, Object event) {
    return Message.builder()
        .messageId(messageId)
        .receiptHandle("receipt-" + messageId)
        .body(objectMapper.writeValueAsString(event))
        .messageAttributes(eventTypeAttribute())
        .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, messageGroupId))
        .build();
  }

  private Map<String, MessageAttributeValue> eventTypeAttribute() {
    return Map.of(
        "eventType",
        MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(EventType.AUCTION_ENDED.name())
            .build());
  }

  private AuctionEndedMessageQueueEvent auctionEndedEvent(String eventId, Long auctionId) {
    return new AuctionEndedMessageQueueEvent(
        eventId,
        auctionId,
        10L,
        20L,
        10000L,
        30000L,
        40L,
        50L,
        50000L,
        AuctionStatus.WON,
        LocalDateTime.of(2026, 8, 5, 9, 0),
        LocalDateTime.of(2026, 8, 5, 10, 0),
        LocalDateTime.of(2026, 8, 1, 9, 0),
        LocalDateTime.of(2026, 8, 5, 10, 0));
  }
}
