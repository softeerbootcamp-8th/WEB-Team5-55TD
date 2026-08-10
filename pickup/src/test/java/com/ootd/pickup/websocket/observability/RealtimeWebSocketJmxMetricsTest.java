package com.ootd.pickup.websocket.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

class RealtimeWebSocketJmxMetricsTest {

  private final WebSocketMessageBrokerStats messageBrokerStats =
      mock(WebSocketMessageBrokerStats.class);
  private final SubProtocolWebSocketHandler.Stats websocketStats =
      mock(SubProtocolWebSocketHandler.Stats.class);
  private final StompSubProtocolHandler.Stats stompStats =
      mock(StompSubProtocolHandler.Stats.class);

  private RealtimeWebSocketJmxMetrics metrics;

  @BeforeEach
  void setUp() {
    given(messageBrokerStats.getWebSocketSessionStats()).willReturn(websocketStats);
    given(messageBrokerStats.getStompSubProtocolStats()).willReturn(stompStats);
    metrics = new RealtimeWebSocketJmxMetrics(messageBrokerStats);
  }

  @Test
  void Spring이_집계한_WebSocket과_STOMP_통계를_그대로_노출한다() {
    given(websocketStats.getWebSocketSessions()).willReturn(3);
    given(websocketStats.getTotalSessions()).willReturn(10);
    given(websocketStats.getNoMessagesReceivedSessions()).willReturn(1);
    given(websocketStats.getLimitExceededSessions()).willReturn(2);
    given(websocketStats.getTransportErrorSessions()).willReturn(4);
    given(stompStats.getTotalConnect()).willReturn(9);
    given(stompStats.getTotalConnected()).willReturn(8);
    given(stompStats.getTotalDisconnect()).willReturn(7);

    assertThat(metrics.getCurrentSessions()).isEqualTo(3);
    assertThat(metrics.getTotalSessions()).isEqualTo(10);
    assertThat(metrics.getConnectFailureSessions()).isEqualTo(1);
    assertThat(metrics.getSendLimitExceededSessions()).isEqualTo(2);
    assertThat(metrics.getTransportErrorSessions()).isEqualTo(4);
    assertThat(metrics.getStompConnectCount()).isEqualTo(9);
    assertThat(metrics.getStompConnectedCount()).isEqualTo(8);
    assertThat(metrics.getStompDisconnectCount()).isEqualTo(7);
  }
}
