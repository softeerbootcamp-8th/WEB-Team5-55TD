package com.ootd.pickup.websocket.observability;

import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

/**
 * Spring이 이미 집계하는 WebSocket 통계를 JMXFetch가 읽을 수 있는 숫자형 attribute로 변환한다.
 *
 * <p>연결 이벤트를 다시 수집하거나 session 목록을 별도로 보관하지 않는다. Spring의 통계를 그대로 사용해 중복 상태와 disconnect 중복 처리 문제를 만들지
 * 않는 것이 이 클래스의 목적이다.
 */
@Component
@ManagedResource(objectName = "com.ootd.pickup.websocket:name=RealtimeWebSocketMetrics")
public class RealtimeWebSocketJmxMetrics {

  private final WebSocketMessageBrokerStats messageBrokerStats;

  public RealtimeWebSocketJmxMetrics(WebSocketMessageBrokerStats messageBrokerStats) {
    this.messageBrokerStats = messageBrokerStats;
  }

  @ManagedAttribute
  public int getCurrentSessions() {
    return websocketStats().getWebSocketSessions();
  }

  @ManagedAttribute
  public int getTotalSessions() {
    return websocketStats().getTotalSessions();
  }

  @ManagedAttribute
  public int getSendLimitExceededSessions() {
    return websocketStats().getLimitExceededSessions();
  }

  @ManagedAttribute
  public int getConnectFailureSessions() {
    return websocketStats().getNoMessagesReceivedSessions();
  }

  @ManagedAttribute
  public int getTransportErrorSessions() {
    return websocketStats().getTransportErrorSessions();
  }

  @ManagedAttribute
  public int getStompConnectCount() {
    return stompStats().getTotalConnect();
  }

  @ManagedAttribute
  public int getStompConnectedCount() {
    return stompStats().getTotalConnected();
  }

  @ManagedAttribute
  public int getStompDisconnectCount() {
    return stompStats().getTotalDisconnect();
  }

  private SubProtocolWebSocketHandler.Stats websocketStats() {
    return messageBrokerStats.getWebSocketSessionStats();
  }

  private StompSubProtocolHandler.Stats stompStats() {
    return messageBrokerStats.getStompSubProtocolStats();
  }
}
