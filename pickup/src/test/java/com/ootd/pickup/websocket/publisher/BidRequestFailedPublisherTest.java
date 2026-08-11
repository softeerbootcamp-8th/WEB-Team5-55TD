package com.ootd.pickup.websocket.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.ootd.pickup.bid.event.BidRequestFailedNotificationEvent;
import com.ootd.pickup.bid.websocket.dto.BidRequestFailedMessage;
import com.ootd.pickup.bid.websocket.publisher.BidRequestFailedPublisher;
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
class BidRequestFailedPublisherTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RealtimeNotificationMetrics metrics =
      new RealtimeNotificationMetrics(meterRegistry);
  private BidRequestFailedPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new BidRequestFailedPublisher(messagingTemplate, metrics);
  }

  @Test
  void 실패_이벤트를_요청한_회원에게만_유니캐스트한다() {
    // given
    BidRequestFailedNotificationEvent event = createEvent();

    // when
    publisher.publish(event);

    // then
    ArgumentCaptor<BidRequestFailedMessage> messageCaptor =
        ArgumentCaptor.forClass(BidRequestFailedMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq("2"), eq("/queue/bid-requests"), messageCaptor.capture());
    BidRequestFailedMessage message = messageCaptor.getValue();
    assertThat(message.type()).isEqualTo("BID_REQUEST_FAILED");
    assertThat(message.bidRequestId()).isEqualTo(10L);
    assertThat(message.failureCode()).isEqualTo("OUTBID_EXISTS");
  }

  @Test
  void Broker_발행이_실패하면_실패를_기록하고_예외를_전파한다() {
    // given
    BidRequestFailedNotificationEvent event = createEvent();
    willThrow(new IllegalStateException("broker unavailable"))
        .given(messagingTemplate)
        .convertAndSendToUser(
            eq("2"), eq("/queue/bid-requests"), eq(BidRequestFailedMessage.fromEvent(event)));

    // when & then
    assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalStateException.class);
  }

  private BidRequestFailedNotificationEvent createEvent() {
    return new BidRequestFailedNotificationEvent(
        "event-id",
        1L,
        2L,
        10L,
        10_500L,
        "OUTBID_EXISTS",
        "이미 더 높은 입찰이 존재합니다.",
        LocalDateTime.now());
  }
}
