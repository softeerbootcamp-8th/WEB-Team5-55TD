package com.ootd.pickup.websocket.config;

import com.ootd.pickup.websocket.observability.RealtimeWebSocketJmxMetrics;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;
import org.springframework.jmx.support.RegistrationPolicy;
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

  @Bean
  MBeanExporter realtimeWebSocketMBeanExporter(RealtimeWebSocketJmxMetrics metrics) {
    // 전역 JMX 자동 탐색을 켜면 HikariCP처럼 이미 등록된 MBean까지 다시 처리할 수 있어 이 MBean만 명시적으로 내보낸다.
    MBeanExporter exporter = new MBeanExporter();
    exporter.setBeans(Map.of("com.ootd.pickup.websocket:name=RealtimeWebSocketMetrics", metrics));
    exporter.setAssembler(new MetadataMBeanInfoAssembler(new AnnotationJmxAttributeSource()));
    exporter.setRegistrationPolicy(RegistrationPolicy.IGNORE_EXISTING);
    return exporter;
  }

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
