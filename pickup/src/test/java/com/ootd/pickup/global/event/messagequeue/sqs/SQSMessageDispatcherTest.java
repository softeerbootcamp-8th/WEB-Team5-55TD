package com.ootd.pickup.global.event.messagequeue.sqs;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.global.event.DomainEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.EventType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SQSMessageDispatcherTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  /** 받은 이벤트를 모아두는 핸들러. */
  private static final class RecordingHandler
      implements EventHandler<AuctionEndedMessageQueueEvent> {

    private final List<AuctionEndedMessageQueueEvent> received = new ArrayList<>();

    @Override
    public Class<AuctionEndedMessageQueueEvent> eventClass() {
      return AuctionEndedMessageQueueEvent.class;
    }

    @Override
    public void handle(AuctionEndedMessageQueueEvent event) {
      received.add(event);
    }
  }

  @SafeVarargs
  private SQSMessageDispatcher dispatcherWith(EventHandler<? extends DomainEvent>... handlers) {
    return new SQSMessageDispatcher(objectMapper, List.of(handlers));
  }

  @Test
  void eventType_속성이_가리키는_타입으로_본문을_되돌린다() {
    // given
    RecordingHandler handler = new RecordingHandler();
    AuctionEndedMessageQueueEvent event = auctionEndedEvent("event-1", 1024L);

    // when
    dispatcherWith(handler).dispatch(message(event));

    // then
    assertThat(handler.received).containsExactly(event);
  }

  @Test
  void 타입이_맞는_핸들러_전부에게_넘긴다() {
    // given
    RecordingHandler handler1 = new RecordingHandler();
    RecordingHandler handler2 = new RecordingHandler();
    AuctionEndedMessageQueueEvent event = auctionEndedEvent("event-1", 1024L);

    // when
    dispatcherWith(handler1, handler2).dispatch(message(event));

    // then
    assertThat(handler1.received).hasSize(1);
    assertThat(handler2.received).hasSize(1);
  }

  @Test
  void 처리할_핸들러가_없으면_예외가_발생한다() {
    // given — 조용히 버리면 유실이 허용되지 않는 이벤트가 사라진다
    AuctionEndedMessageQueueEvent event = auctionEndedEvent("event-1", 1024L);

    // when & then
    assertThatThrownBy(() -> dispatcherWith().dispatch(message(event)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 되돌릴_수_없는_메시지는_예외가_발생한다() {
    // given — 본문이 깨져 있으면 재시도해도 낫지 않는다
    Message broken =
        Message.builder()
            .messageId("message-1")
            .body("깨진 본문")
            .messageAttributes(eventTypeAttribute())
            .build();

    // when & then
    assertThatThrownBy(() -> dispatcherWith(new RecordingHandler()).dispatch(broken))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void eventType_속성이_없으면_예외가_발생한다() {
    // given — 되돌릴 타입을 알 수 없다
    Message noAttribute =
        Message.builder()
            .messageId("message-1")
            .body(objectMapper.writeValueAsString(auctionEndedEvent("event-1", 1024L)))
            .build();

    // when & then
    assertThatThrownBy(() -> dispatcherWith(new RecordingHandler()).dispatch(noAttribute))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 아는_eventType이_아니면_예외가_발생한다() {
    // given — 상수 이름이 바뀌었거나 다른 버전이 보낸 메시지다
    Message unknownType =
        Message.builder()
            .messageId("message-1")
            .body(objectMapper.writeValueAsString(auctionEndedEvent("event-1", 1024L)))
            .messageAttributes(
                Map.of(
                    "eventType",
                    MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("AUCTION_VAPORIZED")
                        .build()))
            .build();

    // when & then
    assertThatThrownBy(() -> dispatcherWith(new RecordingHandler()).dispatch(unknownType))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Message message(Object event) {
    return Message.builder()
        .messageId("message-1")
        .body(objectMapper.writeValueAsString(event))
        .messageAttributes(eventTypeAttribute())
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
