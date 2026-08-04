package com.ootd.pickup.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.ootd.pickup.bid.event.BidPlacedEvent;
import com.ootd.pickup.realtime.dto.AuctionRealtimeMessageType;
import com.ootd.pickup.realtime.dto.BidPlacedMessage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class AuctionRealtimeEventListenerTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  @InjectMocks private AuctionRealtimeEventListener listener;

  @Test
  void 입찰_이벤트를_처리하면_입찰_메시지를_경매_topic으로_전송한다() {
    // given
    UUID eventId = UUID.fromString("126789c9-796a-4f47-9f4a-b262ef621ad1");
    LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 4, 15, 30, 0, 123_456_789);
    LocalDateTime createdAt = LocalDateTime.of(2026, 8, 4, 15, 29, 59, 987_654_321);
    BidPlacedEvent event =
        new BidPlacedEvent(eventId, 42L, 100L, "입찰자**", 50_000L, occurredAt, createdAt);
    ArgumentCaptor<BidPlacedMessage> messageCaptor =
        ArgumentCaptor.forClass(BidPlacedMessage.class);

    // when
    listener.handle(event);

    // then
    verify(messagingTemplate).convertAndSend(eq("/topic/auctions/42"), messageCaptor.capture());
    assertThat(messageCaptor.getValue())
        .isEqualTo(
            new BidPlacedMessage(
                eventId,
                AuctionRealtimeMessageType.BID_PLACED,
                42L,
                occurredAt,
                100L,
                "입찰자**",
                50_000L,
                createdAt));
  }

  @Test
  void 브로커_전송에_실패하면_예외가_이벤트_호출자에게_전파되지_않는다() {
    // given
    BidPlacedEvent event =
        new BidPlacedEvent(
            UUID.fromString("126789c9-796a-4f47-9f4a-b262ef621ad1"),
            42L,
            100L,
            "입찰자**",
            50_000L,
            LocalDateTime.of(2026, 8, 4, 15, 30),
            LocalDateTime.of(2026, 8, 4, 15, 29));
    doThrow(new MessagingException("broker failure"))
        .when(messagingTemplate)
        .convertAndSend(eq("/topic/auctions/42"), any(BidPlacedMessage.class));

    // when & then
    assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
  }
}
