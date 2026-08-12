package com.ootd.pickup.global.event.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.BidRequestSucceededNotificationEvent;
import com.ootd.pickup.auction.event.WinningBidSnapshot;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.global.event.DomainEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.NotificationEventDispatcher;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RedisEventSubscriberTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private final List<BidRequestSucceededNotificationEvent> receivedEvents = new ArrayList<>();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RealtimeNotificationMetrics metrics =
      new RealtimeNotificationMetrics(meterRegistry);

  @Mock private Message message;

  private RedisEventSubscriber subscriber;

  @BeforeEach
  void setUp() {
    subscriber = createSubscriber(List.of(new TestEventHandler(receivedEvents::add)));
  }

  @Test
  void Redis_메시지를_알림_이벤트로_역직렬화해_처리기에_전달한다() {
    BidRequestSucceededNotificationEvent event = createEvent();
    givenMessage("pickup:notification:AUCTION:42", envelopeOf(event));

    subscriber.onMessage(message, null);

    assertThat(receivedEvents).containsExactly(event);
    assertThat(receiveCount("success", "BID_REQUEST_SUCCEEDED")).isEqualTo(1);
  }

  @Test
  void 한_처리기가_실패해도_다른_처리기는_이벤트를_받는다() {
    EventHandler<BidRequestSucceededNotificationEvent> failingHandler =
        new TestEventHandler(
            event -> {
              throw new IllegalStateException("handler failure");
            });
    subscriber =
        createSubscriber(List.of(failingHandler, new TestEventHandler(receivedEvents::add)));
    BidRequestSucceededNotificationEvent event = createEvent();
    givenMessage("pickup:notification:AUCTION:42", envelopeOf(event));

    assertThatCode(() -> subscriber.onMessage(message, null)).doesNotThrowAnyException();
    assertThat(receivedEvents).containsExactly(event);
  }

  @Test
  void 지원하지_않는_이벤트는_처리기에_전달하지_않는다() {
    given(message.getBody())
        .willReturn(
            "{\"eventType\":\"UNKNOWN_EVENT\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8));
    given(message.getChannel())
        .willReturn("pickup:notification:AUCTION:42".getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    assertThat(receivedEvents).isEmpty();
    assertThat(receiveCount("deserialize_failure", "unknown")).isEqualTo(1);
  }

  @Test
  void 채널과_이벤트의_애그리거트가_다르면_처리기에_전달하지_않는다() {
    BidRequestSucceededNotificationEvent event = createEvent();
    givenMessage("pickup:notification:AUCTION:99", envelopeOf(event));

    subscriber.onMessage(message, null);

    assertThat(receivedEvents).isEmpty();
    assertThat(receiveCount("channel_mismatch", "BID_REQUEST_SUCCEEDED")).isEqualTo(1);
  }

  @Test
  void 필수_값이_없는_이벤트는_처리기에_전달하지_않는다() {
    given(message.getBody()).willReturn("null".getBytes(StandardCharsets.UTF_8));
    given(message.getChannel())
        .willReturn("pickup:notification:AUCTION:42".getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    assertThat(receivedEvents).isEmpty();
  }

  @Test
  void 올바르지_않은_JSON은_처리기에_전달하지_않는다() {
    given(message.getBody()).willReturn("not-json".getBytes(StandardCharsets.UTF_8));
    given(message.getChannel())
        .willReturn("pickup:notification:AUCTION:42".getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    assertThat(receivedEvents).isEmpty();
  }

  private RedisEventSubscriber createSubscriber(
      List<EventHandler<? extends DomainEvent>> eventHandlers) {
    NotificationEnvelopeReader envelopeReader = new NotificationEnvelopeReader(objectMapper);
    NotificationChannelResolver channelResolver = new NotificationChannelResolver();
    NotificationEventDispatcher eventDispatcher = new NotificationEventDispatcher(eventHandlers);
    return new RedisEventSubscriber(envelopeReader, channelResolver, eventDispatcher, metrics);
  }

  private double receiveCount(String outcome, String eventType) {
    return meterRegistry
        .get("pickup.redis.notification.receive")
        .tags("outcome", outcome, "event_type", eventType)
        .counter()
        .count();
  }

  private NotificationEnvelope envelopeOf(BidRequestSucceededNotificationEvent event) {
    return new NotificationEnvelope(event.eventType(), objectMapper.valueToTree(event));
  }

  private void givenMessage(String channel, NotificationEnvelope envelope) {
    given(message.getBody()).willReturn(objectMapper.writeValueAsBytes(envelope));
    given(message.getChannel()).willReturn(channel.getBytes(StandardCharsets.UTF_8));
  }

  private BidRequestSucceededNotificationEvent createEvent() {
    LocalDateTime startedAt = LocalDateTime.of(2026, 8, 5, 10, 0);
    LocalDateTime endedAt = LocalDateTime.of(2026, 8, 5, 11, 0);
    LocalDateTime bidCreatedAt = LocalDateTime.of(2026, 8, 5, 10, 30);
    LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 5, 10, 30, 1);
    return new BidRequestSucceededNotificationEvent(
        "event-id",
        42L,
        100L,
        10_000L,
        15_000L,
        20_000L,
        AuctionStatus.ONGOING,
        startedAt,
        endedAt,
        startedAt.minusDays(1),
        new WinningBidSnapshot(7L, 9L, "피카츄마스터", 20_000L, BidStatus.HIGHEST, bidCreatedAt),
        null,
        occurredAt);
  }

  private record TestEventHandler(Consumer<BidRequestSucceededNotificationEvent> consumer)
      implements EventHandler<BidRequestSucceededNotificationEvent> {

    @Override
    public Class<BidRequestSucceededNotificationEvent> eventClass() {
      return BidRequestSucceededNotificationEvent.class;
    }

    @Override
    public void handle(BidRequestSucceededNotificationEvent event) {
      consumer.accept(event);
    }
  }
}
