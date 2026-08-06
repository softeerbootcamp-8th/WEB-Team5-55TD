package com.ootd.pickup.websocket.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties({RealtimeWebSocketProperties.class})
@RequiredArgsConstructor
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private static final long HEARTBEAT_INTERVAL_MILLIS = 10_000;
  private final RealtimeWebSocketProperties properties;
  private final TaskScheduler realtimeHeartBeatTaskScheduler;
  private final AuctionSubscriptionInterceptor auctionSubscriptionInterceptor;

  /*
  웹소켓 연결 엔드포인트 설정
   */
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // 클라이언트가 서버의 @MessageMapping 메서드로 보내는 영역
    registry.setApplicationDestinationPrefixes("/app");
    registry
        // Broker가 여러 구독자에게 방송하는 영역 -> Simple Broker가 처리
        .enableSimpleBroker("/topic")
        .setTaskScheduler(realtimeHeartBeatTaskScheduler)
        .setHeartbeatValue(new long[] {HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS});
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(auctionSubscriptionInterceptor);
  }
}
