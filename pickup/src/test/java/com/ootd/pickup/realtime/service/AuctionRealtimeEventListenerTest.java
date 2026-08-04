package com.ootd.pickup.realtime.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.ootd.pickup.bid.event.BidPlacedEvent;
import com.ootd.pickup.realtime.dto.BidPlacedMessage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class AuctionRealtimeEventListenerTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  @InjectMocks private AuctionRealtimeEventListener listener;

  @Test
  void 실시간_메시지_전송_실패가_이벤트_호출자에게_전파되지_않는다() {
    // given
    BidPlacedEvent event =
        new BidPlacedEvent(
            UUID.randomUUID(),
            42L,
            100L,
            "입찰자**",
            50_000L,
            LocalDateTime.now(),
            LocalDateTime.now());
    doThrow(new IllegalStateException("broker failure"))
        .when(messagingTemplate)
        .convertAndSend(eq("/topic/auctions/42"), any(BidPlacedMessage.class));

    // when & then
    assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
  }
}
