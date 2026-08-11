package com.ootd.pickup.websocket.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.BidRequestSucceededNotificationEvent;
import com.ootd.pickup.auction.event.WinningBidSnapshot;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.websocket.dto.BidRequestSucceededMessage;
import com.ootd.pickup.bid.websocket.publisher.BidRequestSucceededPublisher;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class BidRequestSucceededPublisherTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RealtimeNotificationMetrics metrics =
      new RealtimeNotificationMetrics(meterRegistry);
  private BidRequestSucceededPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new BidRequestSucceededPublisher(messagingTemplate, metrics);
  }

  @Test
  void 입찰_요청_성공_이벤트를_공개_메시지로_바꿔_경매_topic에_전송한다() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 30);
    BidRequestSucceededNotificationEvent event =
        new BidRequestSucceededNotificationEvent(
            "event-id",
            42L,
            100L,
            10_000L,
            15_000L,
            20_000L,
            AuctionStatus.ONGOING,
            now.minusMinutes(30),
            now.plusMinutes(30),
            now.minusDays(1),
            new WinningBidSnapshot(7L, 9L, "피카츄마스터", 20_000L, BidStatus.HIGHEST, now),
            5L,
            now);

    publisher.publish(event);

    ArgumentCaptor<BidRequestSucceededMessage> messageCaptor =
        ArgumentCaptor.forClass(BidRequestSucceededMessage.class);
    verify(messagingTemplate).convertAndSend(eq("/topic/auctions/42"), messageCaptor.capture());
    BidRequestSucceededMessage message = messageCaptor.getValue();
    assertThat(message.type()).isEqualTo("BID_REQUEST_SUCCEEDED");
    assertThat(message.bidRequestId()).isEqualTo(5L);
    assertThat(message.currentPrice()).isEqualTo(20_000L);
    assertThat(message.latestBid().nicknameMasked()).isEqualTo("피***터");
    assertThat(brokerPublishCount("success")).isEqualTo(1);
  }

  @Test
  void Broker_발행이_실패하면_실패를_기록하고_예외를_전파한다() {
    BidRequestSucceededNotificationEvent event = createEvent();
    willThrow(new IllegalStateException("broker unavailable"))
        .given(messagingTemplate)
        .convertAndSend(
            eq("/topic/auctions/42"), eq(BidRequestSucceededMessage.fromEvent(event)));

    assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalStateException.class);
    assertThat(brokerPublishCount("failure")).isEqualTo(1);
  }

  private double brokerPublishCount(String outcome) {
    return meterRegistry
        .get("pickup.websocket.broker.publish")
        .tags("outcome", outcome, "event_type", "BID_REQUEST_SUCCEEDED")
        .counter()
        .count();
  }

  private BidRequestSucceededNotificationEvent createEvent() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 30);
    return new BidRequestSucceededNotificationEvent(
        "event-id",
        42L,
        100L,
        10_000L,
        15_000L,
        20_000L,
        AuctionStatus.ONGOING,
        now.minusMinutes(30),
        now.plusMinutes(30),
        now.minusDays(1),
        new WinningBidSnapshot(7L, 9L, "피카츄마스터", 20_000L, BidStatus.HIGHEST, now),
        null,
        now);
  }
}
