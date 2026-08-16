package com.ootd.pickup.global.event.messagequeue.sqs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.global.event.DomainEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.messagequeue.sqs.config.SQSProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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

  /** 이벤트 id별로 다른 동작을 지정할 수 있는 핸들러. 지정하지 않은 id는 {@code defaultAction}을 탄다. */
  private static final class RoutingHandler implements EventHandler<AuctionEndedMessageQueueEvent> {

    private final Map<String, Consumer<AuctionEndedMessageQueueEvent>> actionsByEventId =
        new ConcurrentHashMap<>();
    private final Consumer<AuctionEndedMessageQueueEvent> defaultAction;

    RoutingHandler(Consumer<AuctionEndedMessageQueueEvent> defaultAction) {
      this.defaultAction = defaultAction;
    }

    void on(String eventId, Consumer<AuctionEndedMessageQueueEvent> action) {
      actionsByEventId.put(eventId, action);
    }

    @Override
    public Class<AuctionEndedMessageQueueEvent> eventClass() {
      return AuctionEndedMessageQueueEvent.class;
    }

    @Override
    public void handle(AuctionEndedMessageQueueEvent event) {
      actionsByEventId.getOrDefault(event.eventId(), defaultAction).accept(event);
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
            QUEUE_URL,
            "ap-northeast-2",
            Duration.ofSeconds(20),
            Duration.ofSeconds(30),
            10,
            4,
            Duration.ofSeconds(15));
    return new SQSEventConsumer(eventSqsClient, properties, objectMapper, List.of(handlers));
  }

  @SafeVarargs
  private SQSEventConsumer consumerWith(
      Duration workerAwaitTimeout, EventHandler<? extends DomainEvent>... handlers) {
    SQSProperties properties =
        new SQSProperties(
            QUEUE_URL,
            "ap-northeast-2",
            Duration.ofSeconds(20),
            Duration.ofSeconds(30),
            10,
            4,
            workerAwaitTimeout);
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
  void eventType_속성이_없는_메시지는_지우지_않는다() {
    // given — 되돌릴 타입을 알 수 없다. 지우면 이벤트가 사라지므로 DLQ 로 보내야 한다
    Message noAttribute =
        Message.builder()
            .messageId("message-1")
            .receiptHandle("receipt-1")
            .body(objectMapper.writeValueAsString(auctionEndedEvent("event-1", 1024L)))
            .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, "AUCTION:1024"))
            .build();
    givenMessages(noAttribute);

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    assertThat(auctionEndedHandler.received).isEmpty();
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  @Test
  void 아는_eventType이_아닌_메시지는_지우지_않는다() {
    // given — 상수 이름이 바뀌었거나 다른 버전이 보낸 메시지다. 재시도해도 낫지 않으므로 DLQ 로 보낸다
    Message unknownType =
        Message.builder()
            .messageId("message-1")
            .receiptHandle("receipt-1")
            .body(objectMapper.writeValueAsString(auctionEndedEvent("event-1", 1024L)))
            .messageAttributes(
                Map.of(
                    "eventType",
                    MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("AUCTION_VAPORIZED")
                        .build()))
            .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, "AUCTION:1024"))
            .build();
    givenMessages(unknownType);

    // when
    consumerWith(auctionEndedHandler).consumeOnce();

    // then
    assertThat(auctionEndedHandler.received).isEmpty();
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
    assertThat(auctionEndedHandler.received)
        .extracting(AuctionEndedMessageQueueEvent::eventId)
        .containsExactly("event-2");
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

  @Test
  void 같은_그룹_id는_항상_같은_샤드로_간다() {
    // given
    String messageGroupId = "AUCTION:1024";

    // when
    int first = SQSEventConsumer.shardIndexOf(messageGroupId, 4);
    int second = SQSEventConsumer.shardIndexOf(messageGroupId, 4);

    // then
    assertThat(first).isEqualTo(second);
    assertThat(first).isBetween(0, 3);
  }

  @Test
  void 여러_그룹이_모든_샤드에_고르게_분포된다() {
    // given
    int parallelism = 4;
    Set<Integer> usedShards = new HashSet<>();

    // when
    for (long auctionId = 1; auctionId <= 1000; auctionId++) {
      usedShards.add(SQSEventConsumer.shardIndexOf("AUCTION:" + auctionId, parallelism));
    }

    // then
    assertThat(usedShards).containsExactlyInAnyOrder(0, 1, 2, 3);
  }

  @Test
  void 서로_다른_그룹이_동시에_처리된다() {
    // given — 순차 처리라면 상대가 도착하기 전에 바리어 대기가 타임아웃돼 둘 다 실패한다
    int parallelism = 4;
    String groupA = "AUCTION:1";
    String groupB = differentShardGroup(groupA, parallelism);
    CyclicBarrier barrier = new CyclicBarrier(2);
    List<String> received = Collections.synchronizedList(new ArrayList<>());
    RoutingHandler handler =
        new RoutingHandler(
            event -> {
              awaitBarrier(barrier);
              received.add(event.eventId());
            });
    givenMessages(
        message("message-1", groupA, auctionEndedEvent("event-1", 1L)),
        message("message-2", groupB, auctionEndedEvent("event-2", 2L)));

    // when
    consumerWith(handler).consumeOnce();

    // then
    assertThat(received).containsExactlyInAnyOrder("event-1", "event-2");
  }

  @Test
  void 같은_그룹은_동시성_환경에서도_순서가_유지된다() {
    // given — 처리 순서가 섞일 여지를 주기 위해 무작위 지연을 준다
    List<String> processedOrder = Collections.synchronizedList(new ArrayList<>());
    RoutingHandler handler =
        new RoutingHandler(
            event -> {
              try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(6));
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
              processedOrder.add(event.eventId());
            });
    givenMessages(
        message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)),
        message("message-2", "AUCTION:1024", auctionEndedEvent("event-2", 1024L)),
        message("message-3", "AUCTION:1024", auctionEndedEvent("event-3", 1024L)),
        message("message-4", "AUCTION:1024", auctionEndedEvent("event-4", 1024L)),
        message("message-5", "AUCTION:1024", auctionEndedEvent("event-5", 1024L)));

    // when
    consumerWith(handler).consumeOnce();

    // then
    assertThat(processedOrder)
        .containsExactly("event-1", "event-2", "event-3", "event-4", "event-5");
  }

  @Test
  void 느린_그룹이_다른_그룹을_굶기지_않는다() throws Exception {
    // given
    int parallelism = 4;
    String slowGroup = "AUCTION:1";
    String fastGroup = differentShardGroup(slowGroup, parallelism);
    CountDownLatch releaseSlowGroup = new CountDownLatch(1);
    List<String> completedOrder = Collections.synchronizedList(new ArrayList<>());

    RoutingHandler handler = new RoutingHandler(event -> completedOrder.add(event.eventId()));
    handler.on(
        "slow-event",
        event -> {
          try {
            releaseSlowGroup.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          completedOrder.add(event.eventId());
        });
    givenMessages(
        message("message-1", slowGroup, auctionEndedEvent("slow-event", 1L)),
        message("message-2", fastGroup, auctionEndedEvent("fast-event", 2L)));

    // when
    Thread consumerThread = new Thread(() -> consumerWith(handler).consumeOnce());
    consumerThread.start();
    long deadline = System.currentTimeMillis() + 2000;
    while (completedOrder.isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }

    // then — 느린 그룹을 풀어주기 전에 빠른 그룹이 먼저 끝나 있어야 한다
    assertThat(completedOrder).containsExactly("fast-event");

    // when
    releaseSlowGroup.countDown();
    consumerThread.join(5000);

    // then
    assertThat(completedOrder).containsExactly("fast-event", "slow-event");
  }

  @Test
  void 워커_대기_시간을_넘기면_그_워커의_메시지는_삭제_대상에서_빠진다() {
    // given — 정체된 그룹은 워커 대기 시간을 아주 짧게 잡아 곧바로 포기하게 한다
    int parallelism = 4;
    String stuckGroup = "AUCTION:1";
    String fastGroup = differentShardGroup(stuckGroup, parallelism);
    CountDownLatch neverReleasedInTime = new CountDownLatch(1);

    RoutingHandler handler = new RoutingHandler(event -> {});
    handler.on(
        "stuck-event",
        event -> {
          try {
            neverReleasedInTime.await(1, TimeUnit.SECONDS);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        });
    givenMessages(
        message("message-1", stuckGroup, auctionEndedEvent("stuck-event", 1L)),
        message("message-2", fastGroup, auctionEndedEvent("fast-event", 2L)));

    // when
    consumerWith(Duration.ofMillis(100), handler).consumeOnce();

    // then
    assertThat(deletedMessageIds()).containsExactly("message-2");
  }

  @Test
  void 생성하면_워커_스레드가_바로_뜨고_stop_이후엔_폴링_워커_스레드가_모두_정리된다() throws Exception {
    // given
    givenMessages();
    SQSEventConsumer consumer = consumerWith(auctionEndedHandler);

    // then — 첫 작업을 기다리지 않고 생성 시점에 워커가 이미 떠 있다
    Set<String> afterConstruction = liveThreadNames();
    for (int shard = 0; shard < 4; shard++) {
      assertThat(afterConstruction).contains(consumer.workerThreadName(shard));
    }

    // when
    consumer.start();
    Thread.sleep(100);

    // then
    assertThat(liveThreadNames()).contains("sqs-event-consumer");

    // when
    consumer.stop();

    // then
    Set<String> afterStop = liveThreadNames();
    assertThat(afterStop).doesNotContain("sqs-event-consumer");
    for (int shard = 0; shard < 4; shard++) {
      assertThat(afterStop).doesNotContain(consumer.workerThreadName(shard));
    }
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(2, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new IllegalStateException("바리어 대기 실패", exception);
    }
  }

  /** {@code messageGroupId}와 다른 샤드로 가는 그룹 id를 찾는다. */
  private static String differentShardGroup(String messageGroupId, int parallelism) {
    int excludedShard = SQSEventConsumer.shardIndexOf(messageGroupId, parallelism);
    for (long auctionId = 1; auctionId < 10_000; auctionId++) {
      String candidate = "AUCTION:" + auctionId;
      if (SQSEventConsumer.shardIndexOf(candidate, parallelism) != excludedShard) {
        return candidate;
      }
    }
    throw new IllegalStateException("다른 샤드로 가는 그룹을 찾지 못했습니다");
  }

  private static Set<String> liveThreadNames() {
    return Thread.getAllStackTraces().keySet().stream()
        .map(Thread::getName)
        .collect(Collectors.toSet());
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
