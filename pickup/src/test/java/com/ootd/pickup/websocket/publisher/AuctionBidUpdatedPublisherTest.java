package com.ootd.pickup.websocket.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
import com.ootd.pickup.auction.event.WinningBidSnapshot;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.websocket.dto.AuctionBidUpdatedMessage;
import com.ootd.pickup.bid.websocket.publisher.AuctionBidUpdatedPublisher;
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
class AuctionBidUpdatedPublisherTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RealtimeNotificationMetrics metrics =
      new RealtimeNotificationMetrics(meterRegistry);
  private AuctionBidUpdatedPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new AuctionBidUpdatedPublisher(messagingTemplate, metrics);
  }

  @Test
  void 입찰_갱신_이벤트를_공개_메시지로_바꿔_경매_topic에_전송한다() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 30);
    AuctionBidUpdatedNotificationEvent event =
        new AuctionBidUpdatedNotificationEvent(
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
            now);

    publisher.publish(event);

    ArgumentCaptor<AuctionBidUpdatedMessage> messageCaptor =
        ArgumentCaptor.forClass(AuctionBidUpdatedMessage.class);
    verify(messagingTemplate).convertAndSend(eq("/topic/auctions/42"), messageCaptor.capture());
    AuctionBidUpdatedMessage message = messageCaptor.getValue();
    assertThat(message.type()).isEqualTo("AUCTION_BID_UPDATED");
    assertThat(message.currentPrice()).isEqualTo(20_000L);
    assertThat(message.latestBid().nicknameMasked()).isEqualTo("피***터");
    assertThat(brokerPublishCount("success")).isEqualTo(1);
  }

  @Test
  void Broker_발행이_실패하면_실패를_기록하고_예외를_전파한다() {
    AuctionBidUpdatedNotificationEvent event = createEvent();
    willThrow(new IllegalStateException("broker unavailable"))
        .given(messagingTemplate)
        .convertAndSend(eq("/topic/auctions/42"), eq(AuctionBidUpdatedMessage.fromEvent(event)));

    assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalStateException.class);
    assertThat(brokerPublishCount("failure")).isEqualTo(1);
  }

  private double brokerPublishCount(String outcome) {
    return meterRegistry
        .get("pickup.websocket.broker.publish")
        .tags("outcome", outcome, "event_type", "AUCTION_BID_UPDATED")
        .counter()
        .count();
  }

  private AuctionBidUpdatedNotificationEvent createEvent() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 30);
    return new AuctionBidUpdatedNotificationEvent(
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
        now);
  }
}
