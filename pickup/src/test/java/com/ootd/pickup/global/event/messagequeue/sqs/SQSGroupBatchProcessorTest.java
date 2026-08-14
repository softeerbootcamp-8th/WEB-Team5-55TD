package com.ootd.pickup.global.event.messagequeue.sqs;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.EventType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SQSGroupBatchProcessorTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private ExecutorService groupExecutor;

  @BeforeEach
  void 실행기를_준비한다() {
    groupExecutor = Executors.newFixedThreadPool(4);
  }

  @AfterEach
  void 실행기를_정리한다() {
    groupExecutor.shutdownNow();
  }

  /** 지정한 이벤트에서만 실패하는 핸들러. */
  private static final class RecordingHandler
      implements EventHandler<AuctionEndedMessageQueueEvent> {

    private final List<String> received = new CopyOnWriteArrayList<>();
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
      received.add(event.eventId());
    }
  }

  /** 지정한 이벤트에서만 {@link #releaseSlow}가 열릴 때까지 처리를 붙잡아 두는 핸들러. */
  private static final class BlockingHandler
      implements EventHandler<AuctionEndedMessageQueueEvent> {

    private final CountDownLatch slowStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSlow = new CountDownLatch(1);
    private final CountDownLatch fastDone = new CountDownLatch(1);
    private final List<String> received = new CopyOnWriteArrayList<>();
    private String slowEventId;

    @Override
    public Class<AuctionEndedMessageQueueEvent> eventClass() {
      return AuctionEndedMessageQueueEvent.class;
    }

    @Override
    public void handle(AuctionEndedMessageQueueEvent event) {
      if (event.eventId().equals(slowEventId)) {
        slowStarted.countDown();
        awaitRelease();
        received.add(event.eventId());
        return;
      }
      received.add(event.eventId());
      fastDone.countDown();
    }

    private void awaitRelease() {
      try {
        releaseSlow.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private SQSGroupBatchProcessor processorWith(
      EventHandler<AuctionEndedMessageQueueEvent> handler) {
    SQSMessageDispatcher dispatcher = new SQSMessageDispatcher(objectMapper, List.of(handler));
    return new SQSGroupBatchProcessor(dispatcher, groupExecutor);
  }

  @Test
  void 처리에_성공한_메시지를_결과에_담는다() {
    // given
    RecordingHandler handler = new RecordingHandler();
    List<Message> messages =
        List.of(message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)));

    // when
    List<Message> consumed = processorWith(handler).process(messages);

    // then
    assertThat(consumed).extracting(Message::messageId).containsExactly("message-1");
  }

  @Test
  void 핸들러가_실패한_메시지는_결과에서_빠진다() {
    // given — 결과에서 빠져야 호출자가 그 메시지를 삭제하지 않는다
    RecordingHandler handler = new RecordingHandler();
    handler.failingEventId = "event-1";
    List<Message> messages =
        List.of(message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)));

    // when
    List<Message> consumed = processorWith(handler).process(messages);

    // then
    assertThat(consumed).isEmpty();
  }

  @Test
  void 같은_그룹의_앞선_메시지가_실패하면_뒤_메시지를_처리하지_않는다() {
    // given — 계속 처리하면 앞 이벤트가 재전달돼 다시 처리될 때 순서가 역전된다
    RecordingHandler handler = new RecordingHandler();
    handler.failingEventId = "event-1";
    List<Message> messages =
        List.of(
            message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)),
            message("message-2", "AUCTION:1024", auctionEndedEvent("event-2", 1024L)));

    // when
    List<Message> consumed = processorWith(handler).process(messages);

    // then
    assertThat(handler.received).isEmpty();
    assertThat(consumed).isEmpty();
  }

  @Test
  void 다른_그룹의_실패는_영향을_주지_않는다() {
    // given — 경매가 다르면 FIFO 그룹도 달라 순서를 함께 지킬 필요가 없다
    RecordingHandler handler = new RecordingHandler();
    handler.failingEventId = "event-1";
    List<Message> messages =
        List.of(
            message("message-1", "AUCTION:1024", auctionEndedEvent("event-1", 1024L)),
            message("message-2", "AUCTION:2048", auctionEndedEvent("event-2", 2048L)));

    // when
    List<Message> consumed = processorWith(handler).process(messages);

    // then
    assertThat(handler.received).containsExactly("event-2");
    assertThat(consumed).extracting(Message::messageId).containsExactly("message-2");
  }

  @Test
  void 한_그룹의_처리가_느려도_다른_그룹은_기다리지_않는다() throws InterruptedException {
    // given — 그룹이 다른 두 메시지를 같은 배치에 담아, 한쪽은 처리를 붙잡아 둔다
    BlockingHandler handler = new BlockingHandler();
    handler.slowEventId = "event-slow";
    List<Message> messages =
        List.of(
            message("message-1", "AUCTION:1024", auctionEndedEvent("event-slow", 1024L)),
            message("message-2", "AUCTION:2048", auctionEndedEvent("event-fast", 2048L)));
    SQSGroupBatchProcessor processor = processorWith(handler);
    Thread processThread = new Thread(() -> processor.process(messages));

    // when
    processThread.start();

    // then — 느린 그룹이 아직 안 끝난 상태에서 다른 그룹은 곧바로 끝난다
    assertThat(handler.slowStarted.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(handler.fastDone.await(2, TimeUnit.SECONDS)).isTrue();

    handler.releaseSlow.countDown();
    processThread.join(Duration.ofSeconds(2).toMillis());
    assertThat(handler.received).containsExactlyInAnyOrder("event-slow", "event-fast");
  }

  private Message message(String messageId, String messageGroupId, Object event) {
    return Message.builder()
        .messageId(messageId)
        .receiptHandle("receipt-" + messageId)
        .body(objectMapper.writeValueAsString(event))
        .messageAttributes(
            Map.of(
                "eventType",
                MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(EventType.AUCTION_ENDED.name())
                    .build()))
        .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, messageGroupId))
        .build();
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
